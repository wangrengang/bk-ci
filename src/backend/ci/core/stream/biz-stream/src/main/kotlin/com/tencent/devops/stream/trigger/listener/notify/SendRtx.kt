package com.tencent.devops.stream.trigger.listener.notify

import com.tencent.devops.common.api.enums.ScmType
import com.tencent.devops.common.api.util.DateTimeUtil
import com.tencent.devops.common.notify.enums.NotifyType
import com.tencent.devops.notify.pojo.SendNotifyMessageTemplateRequest
import com.tencent.devops.process.pojo.BuildHistory
import com.tencent.devops.stream.trigger.pojo.enums.StreamCommitCheckState
import com.tencent.devops.stream.trigger.pojo.enums.StreamNotifyTemplateEnum
import com.tencent.devops.stream.util.StreamPipelineUtils

object SendRtx {
    fun getRtxSendRequest(
        state: StreamCommitCheckState,
        receivers: Set<String>,
        projectName: String,
        branchName: String,
        pipelineName: String,
        pipelineId: String,
        build: BuildHistory,
        isMr: Boolean,
        requestId: String,
        openUser: String,
        buildTime: Long?,
        gitUrl: String,
        streamUrl: String,
        content: String?,
        gitProjectId: String,
        scmType: ScmType
    ): SendNotifyMessageTemplateRequest {
        val isSuccess = state == StreamCommitCheckState.SUCCESS
        val titleParams = mapOf(
            "title" to ""
        )
        val bodyParams = mapOf(
            "content" to if (content.isNullOrBlank()) {
                getRtxCustomContent(
                    isSuccess = isSuccess,
                    projectName = projectName,
                    branchName = branchName,
                    pipelineName = pipelineName,
                    pipelineId = pipelineId,
                    build = build,
                    isMr = isMr,
                    requestId = requestId,
                    openUser = openUser,
                    buildTime = buildTime,
                    gitUrl = gitUrl,
                    streamUrl = streamUrl,
                    gitProjectId = gitProjectId
                )
            } else {
                getRtxCustomUserContent(
                    isSuccess = isSuccess,
                    gitProjectId = gitProjectId,
                    pipelineId = pipelineId,
                    build = build,
                    content = content,
                    streamUrl = streamUrl
                )
            }
        )
        return SendNotifyMessageTemplateRequest(
            templateCode = StreamNotifyTemplateEnum.STREAM_V2_BUILD_TEMPLATE.templateCode,
            receivers = receivers.toMutableSet(),
            cc = null,
            titleParams = titleParams,
            bodyParams = bodyParams,
            notifyType = mutableSetOf(NotifyType.WEWORK.name)
        )
    }

    // 为用户的内容增加链接
    private fun getRtxCustomUserContent(
        isSuccess: Boolean,
        gitProjectId: String,
        pipelineId: String,
        build: BuildHistory,
        content: String,
        streamUrl: String
    ): String {
        val state = if (isSuccess) {
            Triple("✔", "info", "success")
        } else {
            Triple("❌", "warning", "failed")
        }
        val detailUrl = StreamPipelineUtils.genStreamV2BuildUrl(
            homePage = streamUrl,
            gitProjectId = gitProjectId,
            pipelineId = pipelineId,
            buildId = build.id
        )
        return " <font color=\"${state.second}\"> ${state.first} </font> $content \n [查看详情]($detailUrl)"
    }

    private fun getRtxCustomContent(
        isSuccess: Boolean,
        projectName: String,
        branchName: String,
        pipelineName: String,
        pipelineId: String,
        build: BuildHistory,
        isMr: Boolean,
        requestId: String,
        openUser: String,
        buildTime: Long?,
        gitUrl: String,
        streamUrl: String,
        gitProjectId: String
    ): String {
        val state = if (isSuccess) {
            Triple("✔", "info", "success")
        } else {
            Triple("❌", "warning", "failed")
        }
        val request = if (isMr) {
            "Merge requests [[!$requestId]]($gitUrl/$projectName/merge_requests/$requestId)" +
                "opened by $openUser \n"
        } else {
            if (requestId.length >= 8) {
                "Commit [[${requestId.subSequence(0, 7)}]]($gitUrl/$projectName/commit/$requestId)" +
                    "pushed by $openUser \n"
            } else {
                "Manual Triggered by $openUser \n"
            }
        }
        val costTime = "Time cost ${DateTimeUtil.formatMillSecond(buildTime ?: 0)}.  \n   "
        return " <font color=\"${state.second}\"> ${state.first} </font> " +
            "$projectName($branchName) - $pipelineName #${build.buildNum} run ${state.third} \n " +
            request +
            costTime +
            "[查看详情]" +
            "(${
            StreamPipelineUtils.genStreamV2BuildUrl(
                homePage = streamUrl,
                gitProjectId = gitProjectId,
                pipelineId = pipelineId,
                buildId = build.id
            )
            })"
    }
}
