package net.hycrafthd.minecraft_authenticator.microsoft.service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.hycrafthd.minecraft_authenticator.Constants;
import net.hycrafthd.minecraft_authenticator.microsoft.api.MinecraftHasPurchasedResponse;
import net.hycrafthd.minecraft_authenticator.microsoft.api.MinecraftLoginWithXBoxPayload;
import net.hycrafthd.minecraft_authenticator.microsoft.api.MinecraftLoginWithXBoxResponse;
import net.hycrafthd.minecraft_authenticator.microsoft.api.MinecraftProfileResponse;
import net.hycrafthd.minecraft_authenticator.microsoft.api.OAuthErrorResponse;
import net.hycrafthd.minecraft_authenticator.microsoft.api.OAuthTokenResponse;
import net.hycrafthd.minecraft_authenticator.microsoft.api.XSTSAuthorizeErrorResponse;
import net.hycrafthd.minecraft_authenticator.microsoft.api.XSTSAuthorizePayload;
import net.hycrafthd.minecraft_authenticator.microsoft.api.XSTSAuthorizeResponse;
import net.hycrafthd.minecraft_authenticator.util.ConnectionUtil;
import net.hycrafthd.minecraft_authenticator.util.ConnectionUtil.TimeoutValues;
import net.hycrafthd.minecraft_authenticator.util.HttpPayload;
import net.hycrafthd.minecraft_authenticator.util.HttpResponse;
import net.hycrafthd.minecraft_authenticator.util.Parameters;

public class MicrosoftService {
	
	public static URL oAuthLoginUrl() {
		return oAuthLoginUrl(Constants.MICROSOFT_CLIENT_ID, Constants.MICROSOFT_OAUTH_REDIRECT_URL);
	}
	
	public static URL oAuthLoginUrl(String clientId, String redirectUrl) {
		final Parameters parameters = Parameters.create() //
				.add("client_id", clientId) //
				.add("response_type", "code") //
				.add("scope", "XboxLive.signin offline_access") //
				.add("redirect_uri", redirectUrl);
		
		try {
			return ConnectionUtil.urlBuilder(Constants.MICROSOFT_OAUTH_SERVICE, Constants.MICROSOFT_OAUTH_ENDPOINT_AUTHORIZE, parameters);
		} catch (final MalformedURLException ex) {
			throw new AssertionError("This url should never be malformed.", ex);
		}
	}
	
	public static MicrosoftResponse<OAuthTokenResponse, OAuthErrorResponse> oAuthTokenFromCode(String authorizationCode, TimeoutValues timeoutValues) {
		return oAuthTokenFromCode(Constants.MICROSOFT_CLIENT_ID, Constants.MICROSOFT_OAUTH_REDIRECT_URL, authorizationCode, timeoutValues);
	}
	
	public static MicrosoftResponse<OAuthTokenResponse, OAuthErrorResponse> oAuthTokenFromCode(String clientId, String redirectUrl, String authorizationCode, TimeoutValues timeoutValues) {
		final Parameters parameters = Parameters.create() //
				.add("client_id", clientId) //
				.add("code", authorizationCode) //
				.add("grant_type", "authorization_code") //
				.add("redirect_uri", redirectUrl);
		
		return oAuthServiceRequest(parameters, timeoutValues);
	}
	
	public static MicrosoftResponse<OAuthTokenResponse, OAuthErrorResponse> oAuthTokenFromRefreshToken(String refreshToken, TimeoutValues timeoutValues) {
		return oAuthTokenFromRefreshToken(Constants.MICROSOFT_CLIENT_ID, Constants.MICROSOFT_OAUTH_REDIRECT_URL, refreshToken, timeoutValues);
	}
	
	public static MicrosoftResponse<OAuthTokenResponse, OAuthErrorResponse> oAuthTokenFromRefreshToken(String clientId, String redirectUrl, String refreshToken, TimeoutValues timeoutValues) {
		final Parameters parameters = Parameters.create() //
				.add("client_id", clientId) //
				.add("refresh_token", refreshToken) //
				.add("grant_type", "refresh_token") //
				.add("redirect_uri", redirectUrl);
		
		return oAuthServiceRequest(parameters, timeoutValues);
		
	}
	
	private static MicrosoftResponse<OAuthTokenResponse, OAuthErrorResponse> oAuthServiceRequest(Parameters parameters, TimeoutValues timeoutValues) {
		final JsonElement responseElement;
		try {
			final URL url = ConnectionUtil.urlBuilder(Constants.MICROSOFT_OAUTH_SERVICE, Constants.MICROSOFT_OAUTH_ENDPOINT_TOKEN);
			final String responseString = ConnectionUtil.urlEncodedPostRequest(url, ConnectionUtil.JSON_CONTENT_TYPE, parameters, timeoutValues).getAsString();
			responseElement = JsonParser.parseString(responseString);
		} catch (final IOException ex) {
			return MicrosoftResponse.ofException(ex);
		}
		
		try {
			final JsonObject responseObject = responseElement.getAsJsonObject();
			if (responseObject.has("error")) {
				return MicrosoftResponse.ofError(Constants.GSON.fromJson(responseObject, OAuthErrorResponse.class));
			}
			return MicrosoftResponse.ofResponse(Constants.GSON.fromJson(responseObject, OAuthTokenResponse.class));
		} catch (final Exception ex) {
			return MicrosoftResponse.ofException(ex);
		}
	}
	
	public static MicrosoftResponse<String, Integer> xblAuthenticate(String accessToken, TimeoutValues timeoutValues) {
		final JsonObject payloadObject = new JsonObject();
		final JsonObject payloadPropertiesObject = new JsonObject();
		payloadPropertiesObject.addProperty("AuthMethod", "RPS");
		payloadPropertiesObject.addProperty("SiteName", "user.auth.xboxlive.com");
		payloadPropertiesObject.addProperty("RpsTicket", "d=" + accessToken);
		payloadObject.add("Properties", payloadPropertiesObject);
		payloadObject.addProperty("RelyingParty", "http://auth.xboxlive.com");
		payloadObject.addProperty("TokenType", "JWT");
		
		final JsonElement responseElement;
		try {
			final URL url = ConnectionUtil.urlBuilder(Constants.MICROSOFT_XBL_AUTHENTICATE_URL);
			final HttpResponse response = ConnectionUtil.jsonPostRequest(url, HttpPayload.fromJson(payloadObject), timeoutValues);
			if (response.getResponseCode() >= 400) {
				return MicrosoftResponse.ofError(response.getResponseCode());
			}
			responseElement = JsonParser.parseString(response.getAsString());
		} catch (final IOException ex) {
			return MicrosoftResponse.ofException(ex);
		}
		
		try {
			final JsonObject responseObject = responseElement.getAsJsonObject();
			return MicrosoftResponse.ofResponse(responseObject.get("Token").getAsString());
		} catch (final Exception ex) {
			return MicrosoftResponse.ofException(ex);
		}
	}
	
	public static MicrosoftResponse<XSTSAuthorizeResponse, XSTSAuthorizeErrorResponse> xstsAuthorize(XSTSAuthorizePayload payload, TimeoutValues timeoutValues) {
		final String responseString;
		try {
			responseString = ConnectionUtil.jsonPostRequest(ConnectionUtil.urlBuilder(Constants.MICROSOFT_XSTS_AUTHORIZE_URL), HttpPayload.fromString(Constants.GSON.toJson(payload)), timeoutValues).getAsString();
		} catch (final IOException ex) {
			return MicrosoftResponse.ofException(ex);
		}
		
		final JsonElement element = JsonParser.parseString(responseString);
		
		System.out.println(element);
		
		if (element.isJsonObject() && element.getAsJsonObject().get("XErr") != null) {
			final XSTSAuthorizeErrorResponse response = Constants.GSON.fromJson(responseString, XSTSAuthorizeErrorResponse.class);
			return MicrosoftResponse.ofError(response);
		}
		
		final XSTSAuthorizeResponse response = Constants.GSON.fromJson(responseString, XSTSAuthorizeResponse.class);
		return MicrosoftResponse.ofResponse(response);
	}
	
	public static MicrosoftResponse<MinecraftLoginWithXBoxResponse, Integer> minecraftLoginWithXsts(MinecraftLoginWithXBoxPayload payload, TimeoutValues timeoutValues) {
		final String responseString;
		try {
			final HttpResponse response = ConnectionUtil.jsonPostRequest(ConnectionUtil.urlBuilder(Constants.MICROSOFT_MINECRAFT_SERVICE, Constants.MICROSOFT_MINECRAFT_ENDPOINT_XBOX_LOGIN), HttpPayload.fromString(Constants.GSON.toJson(payload)), timeoutValues);
			responseString = response.getAsString();
			if (response.getResponseCode() >= 300) {
				return MicrosoftResponse.ofError(response.getResponseCode());
			}
		} catch (final IOException ex) {
			return MicrosoftResponse.ofException(ex);
		}
		
		final MinecraftLoginWithXBoxResponse response = Constants.GSON.fromJson(responseString, MinecraftLoginWithXBoxResponse.class);
		return MicrosoftResponse.ofResponse(response);
	}
	
	public static MicrosoftResponse<MinecraftHasPurchasedResponse, Integer> minecraftHasPurchased(String accessToken, TimeoutValues timeoutValues) {
		final String responseString;
		try {
			final HttpResponse response = ConnectionUtil.bearerAuthorizationJsonGetRequest(ConnectionUtil.urlBuilder(Constants.MICROSOFT_MINECRAFT_SERVICE, Constants.MICROSOFT_MINECRAFT_ENDPOINT_HAS_PURCHASED), accessToken, timeoutValues);
			responseString = response.getAsString();
			if (response.getResponseCode() >= 300) {
				return MicrosoftResponse.ofError(response.getResponseCode());
			}
		} catch (final IOException ex) {
			return MicrosoftResponse.ofException(ex);
		}
		
		final MinecraftHasPurchasedResponse response = Constants.GSON.fromJson(responseString, MinecraftHasPurchasedResponse.class);
		return MicrosoftResponse.ofResponse(response);
	}
	
	public static MicrosoftResponse<MinecraftProfileResponse, Integer> minecraftProfile(String accessToken, TimeoutValues timeoutValues) {
		final String responseString;
		try {
			final HttpResponse response = ConnectionUtil.bearerAuthorizationJsonGetRequest(ConnectionUtil.urlBuilder(Constants.MICROSOFT_MINECRAFT_SERVICE, Constants.MICROSOFT_MINECRAFT_ENDPOINT_PROFILE), accessToken, timeoutValues);
			responseString = response.getAsString();
			if (response.getResponseCode() >= 300) {
				return MicrosoftResponse.ofError(response.getResponseCode());
			}
		} catch (final IOException ex) {
			return MicrosoftResponse.ofException(ex);
		}
		
		final MinecraftProfileResponse response = Constants.GSON.fromJson(responseString, MinecraftProfileResponse.class);
		return MicrosoftResponse.ofResponse(response);
	}
	
}
