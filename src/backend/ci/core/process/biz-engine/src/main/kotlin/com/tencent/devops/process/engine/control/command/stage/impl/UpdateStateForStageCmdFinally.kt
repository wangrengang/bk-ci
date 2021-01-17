/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.devops.process.engine.control.command.stage.impl

import com.tencent.devops.common.event.dispatcher.pipeline.PipelineEventDispatcher
import com.tencent.devops.common.event.enums.ActionType
import com.tencent.devops.common.log.utils.BuildLogPrinter
import com.tencent.devops.common.pipeline.enums.BuildStatus
import com.tencent.devops.process.engine.common.VMUtils
import com.tencent.devops.process.engine.control.command.CmdFlowState
import com.tencent.devops.process.engine.control.command.stage.StageCmd
import com.tencent.devops.process.engine.control.command.stage.StageContext
import com.tencent.devops.process.engine.pojo.event.PipelineBuildCancelEvent
import com.tencent.devops.process.engine.pojo.event.PipelineBuildFinishEvent
import com.tencent.devops.process.engine.pojo.event.PipelineBuildStageEvent
import com.tencent.devops.process.engine.service.PipelineBuildDetailService
import com.tencent.devops.process.engine.service.PipelineRuntimeService
import com.tencent.devops.process.engine.service.PipelineStageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 每一个Stage结束后续命令处理
 */
@Service
class UpdateStateForStageCmdFinally(
    private val pipelineStageService: PipelineStageService,
    private val pipelineRuntimeService: PipelineRuntimeService,
    private val pipelineBuildDetailService: PipelineBuildDetailService,
    private val pipelineEventDispatcher: PipelineEventDispatcher,
    private val buildLogPrinter: BuildLogPrinter
) : StageCmd {

    override fun canExecute(commandContext: StageContext): Boolean {
        return commandContext.cmdFlowState == CmdFlowState.FINALLY
    }

    override fun execute(commandContext: StageContext) {
        val event = commandContext.event
        val stage = commandContext.stage
        // 更新状态&模型
        updateStageStatus(commandContext = commandContext)
        // Stage 暂停
        if (commandContext.buildStatus == BuildStatus.STAGE_SUCCESS) {
            pipelineStageService.pauseStage(userId = commandContext.event.userId, buildStage = stage)
        } else if (commandContext.buildStatus == BuildStatus.SKIP) { // 跳过
            pipelineStageService.skipStage(userId = event.userId, buildStage = stage)
        } else if (commandContext.buildStatus.isFinish()) { // 当前Stage结束
            // 中断的事件或者快速失败
            if (commandContext.buildStatus.isFailure() || commandContext.fastKill) {
                LOG.info("[${event.buildId}]|[${event.source}]|STAGE_FINALLY|s(${event.stageId})|${commandContext.buildStatus}")
                cancelBuild(commandContext = commandContext)
            } else {
                // 寻找next Stage
                val nextStage = pipelineStageService.getStageBySeq(buildId = event.buildId, stageSeq = stage.seq + 1)
                if (nextStage != null) {
                    val nextStageId = nextStage.stageId
                    LOG.info("[${event.buildId}]|[${event.source}]|NEXT_STAGE|" +
                        "s(${event.stageId})|next_s($nextStageId)|${commandContext.latestSummary}")
                    event.sendNextStage(source = "from_s(${stage.stageId})", stageId = nextStageId)
                } else {
                    // 正常完成构建
                    commandContext.latestSummary = "finally_s(${stage.stageId})"
                    finishBuild(commandContext = commandContext)
                }
            }
        } else {
            LOG.info("[${event.buildId}]|[${event.source}]|STAG_RUNNING|" +
                "s(${event.stageId})|${commandContext.buildStatus}|${commandContext.latestSummary}")
        }
    }

    /**
     * 更新[commandContext]下指定的Stage的状态以及编排模型状态
     */
    private fun updateStageStatus(commandContext: StageContext) {
        val event = commandContext.event
        val buildId = event.buildId
        val stageId = event.stageId
        val stageStatus = commandContext.buildStatus
        // 更新状态
        pipelineStageService.updateStageStatus(buildId = buildId, stageId = stageId, buildStatus = stageStatus)

        // 对未结束的Container进行强制更新
        if (stageStatus.isFailure() || commandContext.fastKill) {
            flushRunningContainer(commandContext, buildId, stageStatus)
        }

        // 如果是因fastKill强制终止，流水线状态标记为失败
        if (commandContext.fastKill) {
            commandContext.buildStatus = BuildStatus.FAILED
        }
        // 开始和结束要刷新编排模型 to do 改进
        if (stageStatus == BuildStatus.RUNNING || stageStatus.isFinish()) {
            val allStageStatus = pipelineBuildDetailService.updateStageStatus(buildId, stageId, stageStatus)
            pipelineRuntimeService.updateBuildHistoryStageState(buildId, allStageStatus = allStageStatus)
        }
    }

    private fun flushRunningContainer(commandContext: StageContext, buildId: String, stageStatus: BuildStatus) {
        commandContext.containers.forEach { c ->
            if (c.status.isRunning()) {
                pipelineRuntimeService.updateContainerStatus(
                    buildId = buildId,
                    stageId = c.stageId,
                    containerId = c.containerId,
                    endTime = LocalDateTime.now(),
                    buildStatus = stageStatus
                )

                if (commandContext.fastKill) {
                    val tag = VMUtils.genStartVMTaskId(c.containerId)
                    buildLogPrinter.addYellowLine(
                        buildId = c.buildId, tag = tag, jobId = tag, executeCount = c.executeCount,
                        message = "job(${c.containerId}) stop by fast kill"
                    )
                }
            }
        }
    }

    /**
     * 发送指定[stageId]的Stage启动事件
     */
    private fun PipelineBuildStageEvent.sendNextStage(source: String, stageId: String) {
        pipelineEventDispatcher.dispatch(
            PipelineBuildStageEvent(
                source = source,
                projectId = projectId,
                pipelineId = pipelineId,
                userId = userId,
                buildId = buildId,
                stageId = stageId,
                actionType = ActionType.START
            )
        )
    }

    /**
     * 将[commandContext]中参数，发送取消构建事件
     */
    private fun cancelBuild(commandContext: StageContext) {
        with(commandContext.event) {
            pipelineEventDispatcher.dispatch(
                PipelineBuildCancelEvent(
                    source = commandContext.latestSummary,
                    projectId = projectId,
                    pipelineId = pipelineId,
                    userId = userId,
                    buildId = buildId,
                    status = commandContext.buildStatus
                )
            )
        }
    }

    /**
     * 完成构建事件
     */
    private fun finishBuild(commandContext: StageContext) {
        with(commandContext.event) {
            pipelineEventDispatcher.dispatch(
                PipelineBuildFinishEvent(
                    source = commandContext.latestSummary,
                    projectId = projectId,
                    pipelineId = pipelineId,
                    userId = userId,
                    buildId = buildId,
                    status = commandContext.buildStatus
                )
            )
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(UpdateStateForStageCmdFinally::class.java)
    }
}
