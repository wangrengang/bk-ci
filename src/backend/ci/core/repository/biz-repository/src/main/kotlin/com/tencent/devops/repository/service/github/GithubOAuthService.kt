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
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.devops.repository.service.github

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.devops.common.api.exception.CustomException
import com.tencent.devops.common.api.exception.OperationException
import com.tencent.devops.common.api.util.HashUtil
import com.tencent.devops.common.api.util.OkhttpUtils
import com.tencent.devops.repository.pojo.github.GithubAppUrl
import com.tencent.devops.repository.pojo.github.GithubOauth
import com.tencent.devops.repository.pojo.github.GithubToken
import com.tencent.devops.scm.config.GitConfig
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.apache.commons.lang3.RandomStringUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import javax.ws.rs.core.Response
import javax.ws.rs.core.UriBuilder

@Service
@Suppress("ALL")
class GithubOAuthService @Autowired constructor(
    private val objectMapper: ObjectMapper,
    private val gitConfig: GitConfig,
    private val githubTokenService: GithubTokenService
) {

    fun getGithubOauth(projectId: String, userId: String, repoHashId: String?): GithubOauth {
        val repoId = if (!repoHashId.isNullOrBlank()) HashUtil.decodeOtherIdToLong(repoHashId!!).toString() else ""
        val state = "$userId,$projectId,$repoId,BK_DEVOPS__${RandomStringUtils.randomAlphanumeric(RANDOM_ALPHA_NUM)}"
        val redirectUrl = "$GITHUB_URL/login/oauth/authorize" +
            "?client_id=${gitConfig.githubClientId}&redirect_uri=${gitConfig.githubWebhookUrl}&state=$state"
        return GithubOauth(redirectUrl)
    }

    fun getGithubAppUrl() = GithubAppUrl(gitConfig.githubAppUrl)

    fun githubCallback(code: String, state: String): Response {
        if (!state.contains(",BK_DEVOPS__")) {
            throw OperationException("TGIT call back contain invalid parameter: $state")
        }

        val arrays = state.split(",")
        val userId = arrays[0]
        val projectId = arrays[1]
        val repoHashId = if (arrays[2].isNotBlank()) HashUtil.encodeOtherLongId(arrays[2].toLong()) else ""
        val githubToken = getAccessTokenImpl(code)

        githubTokenService.createAccessToken(userId, githubToken.accessToken, githubToken.tokenType, githubToken.scope)
        return Response.temporaryRedirect(
            UriBuilder.fromUri("${gitConfig.githubRedirectUrl}/$projectId#popupGithub$repoHashId").build())
                .build()
    }

    private fun getAccessTokenImpl(code: String): GithubToken {
        val url = "$GITHUB_URL/login/oauth/access_token" +
            "?client_id=${gitConfig.githubClientId}&client_secret=${gitConfig.githubClientSecret}&code=$code"

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .post(RequestBody.create(MediaType.parse("application/x-www-form-urlencoded;charset=utf-8"), ""))
            .build()
        OkhttpUtils.doHttp(request).use { response ->
            val data = response.body()!!.string()
            if (!response.isSuccessful) {
                logger.info("Github get code(${response.code()}) and response($data)")
                throw CustomException(Response.Status.fromStatusCode(response.code())
                    ?: Response.Status.BAD_REQUEST, "获取Github access_token失败: $data")
            }
            return objectMapper.readValue(data)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(GithubOAuthService::class.java)
        private const val RANDOM_ALPHA_NUM = 8
        private const val GITHUB_URL = "https://github.com"
    }
}
