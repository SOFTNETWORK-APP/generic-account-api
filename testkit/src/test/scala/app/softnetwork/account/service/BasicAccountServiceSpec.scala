package app.softnetwork.account.service

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.unmarshalling.Unmarshaller
import app.softnetwork.account.message._
import app.softnetwork.account.model._
import app.softnetwork.account.scalatest.{AccountServiceSpec, BasicAccountTestKit}
import app.softnetwork.session.handlers.SessionRefreshTokenDao
import app.softnetwork.session.model.SessionDataCompanion
import app.softnetwork.session.service.{BasicSessionMaterials, SessionMaterials}
import com.softwaremill.session.{RefreshTokenStorage, SessionConfig}
import org.slf4j.{Logger, LoggerFactory}
import org.softnetwork.session.model.Session

import scala.concurrent.ExecutionContext

/** Created by smanciot on 18/04/2020.
  */
trait BasicAccountServiceSpec
    extends AccountServiceSpec[
      BasicAccount,
      BasicAccountProfile,
      DefaultProfileView,
      DefaultAccountDetailsView,
      DefaultAccountView[DefaultProfileView, DefaultAccountDetailsView],
      Session
    ]
    with MockBasicAccountService[Session]
    with BasicAccountTestKit { _: SessionMaterials[Session] =>

  lazy val log: Logger = LoggerFactory getLogger getClass.getName

  override def asSignUp: Unmarshaller[HttpRequest, SU] = as[BasicAccountSignUp]

  override implicit def sessionConfig: SessionConfig = SessionConfig.fromConfig(config)

  override implicit def companion: SessionDataCompanion[Session] = Session

  override implicit def refreshTokenStorage: RefreshTokenStorage[Session] = SessionRefreshTokenDao(
    ts
  )
}

class OneOfCookieSessionBasicAccountServiceSpec
    extends BasicAccountServiceSpec
    with BasicSessionMaterials[Session] {
  override protected def sessionType: Session.SessionType = Session.SessionType.OneOffCookie
  override implicit lazy val ec: ExecutionContext = ts.executionContext
  override protected val manifestWrapper: ManifestW = ManifestW()
}

class OneOfHeaderSessionBasicAccountServiceSpec
    extends BasicAccountServiceSpec
    with BasicSessionMaterials[Session] {
  override protected def sessionType: Session.SessionType = Session.SessionType.OneOffHeader
  override implicit lazy val ec: ExecutionContext = ts.executionContext
  override protected val manifestWrapper: ManifestW = ManifestW()
}

class RefreshableCookieSessionBasicAccountServiceSpec
    extends BasicAccountServiceSpec
    with BasicSessionMaterials[Session] {
  override protected def sessionType: Session.SessionType = Session.SessionType.RefreshableCookie
  override implicit lazy val ec: ExecutionContext = ts.executionContext
  override protected val manifestWrapper: ManifestW = ManifestW()
}

class RefreshableHeaderSessionBasicAccountServiceSpec
    extends BasicAccountServiceSpec
    with BasicSessionMaterials[Session] {
  override protected def sessionType: Session.SessionType = Session.SessionType.RefreshableHeader
  override implicit lazy val ec: ExecutionContext = ts.executionContext
  override protected val manifestWrapper: ManifestW = ManifestW()
}
