package app.softnetwork.account.model

trait OAuthData {
  def provider: String

  def data: Map[String, String]

  def id: String = data.getOrElse("id", "")

  def name: String = data.getOrElse("name", "")

  def email: String = data.getOrElse("email", "")

  def login: Option[String] = email match {
    case "" =>
      name match {
        case "" => None
        case _  => Some(name)
      }
    case _ => Some(email)
  }

}

case class FacebookOAuthData(data: Map[String, String]) extends OAuthData {
  val provider = "facebook"
}

case class GoogleOAuthData(data: Map[String, String]) extends OAuthData {
  val provider = "google"
}

case class GitHubOAuthData(data: Map[String, String]) extends OAuthData {
  val provider = "github"
}

case class InstagramOAuthData(data: Map[String, String]) extends OAuthData {
  val provider = "instagram"
  override val name: String = data.getOrElse("username", "")
}

case class BasicOAuthData(provider: String, data: Map[String, String]) extends OAuthData

trait OAuthDataCompanion {
  def apply(provider: String, data: Map[String, String]): OAuthData = provider match {
    case "facebook"  => FacebookOAuthData(data)
    case "github"    => GitHubOAuthData(data)
    case "google"    => GoogleOAuthData(data)
    case "instagram" => InstagramOAuthData(data)
    case _           => BasicOAuthData(provider, data)
  }
}

object OAuthData extends OAuthDataCompanion
