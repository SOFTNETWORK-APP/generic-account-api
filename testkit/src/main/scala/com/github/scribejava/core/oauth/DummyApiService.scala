package com.github.scribejava.core.oauth

import app.softnetwork.account.spi.OAuth2Service
import com.github.scribejava.core.builder.api.DefaultApi20
import com.github.scribejava.core.model.{OAuthRequest, Response}

class DummyApiService extends OAuth2Service {
  override val networkName: String = "dummy"

  override val instance: DefaultApi20 = new OAuth20ApiUnit()

  override def execute(request: OAuthRequest): Response = new Response(
    200,
    "OK",
    null,
    """{
      |  "name": "me",
      |  "email": "me@dummy.com"
      |}""".stripMargin
  )

}
