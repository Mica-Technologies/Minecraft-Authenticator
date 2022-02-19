package net.hycrafthd.minecraft_authenticator.login;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;

import net.hycrafthd.minecraft_authenticator.login.AuthenticationFile.AuthenticationFileDeserializer;
import net.hycrafthd.minecraft_authenticator.microsoft.MicrosoftAuthenticationFile;
import net.hycrafthd.minecraft_authenticator.microsoft.MicrosoftAuthenticationFile.MicrosoftAuthenticationFileDeserializer;
import net.hycrafthd.minecraft_authenticator.util.AuthenticationUtil;

/**
 * File that contains authentication information.
 */
@JsonAdapter(AuthenticationFileDeserializer.class)
public abstract class AuthenticationFile {
	
	private final Type type;
	private final UUID clientId;
	
	protected AuthenticationFile(Type type, UUID clientId) {
		this.type = type;
		this.clientId = clientId;
	}
	
	public Type getType() {
		return type;
	}
	
	public UUID getClientId() {
		return clientId;
	}
	
	public static enum Type {
		@SerializedName("microsoft")
		MICROSOFT;
	}
	
	/**
	 * Reads an {@link AuthenticationFile} from an input stream. The input stream is not closed.
	 *
	 * @param inputStream InputStream to read the data from
	 * @return An {@link AuthenticationFile} instance
	 * @throws IOException Error if data could not be parsed
	 */
	public static AuthenticationFile read(InputStream inputStream) throws IOException {
		return AuthenticationUtil.readAuthenticationFile(inputStream);
	}
	
	/**
	 * Reads an {@link AuthenticationFile} from a byte array.
	 * 
	 * @param bytes Bytes of the authentication file
	 * @return An {@link AuthenticationFile} instance
	 * @throws IOException Error if data could not be parsed
	 */
	public static AuthenticationFile read(byte[] bytes) throws IOException {
		return AuthenticationUtil.readAuthenticationFile(bytes);
	}
	
	/**
	 * Write this {@link AuthenticationFile} to an output stream. The output stream is not closed.
	 * <p>
	 * Attention: The data is in plain text and can be read by anyone that has access to the output stream data (e.g.
	 * writing to a file). Even though this data does not contain any credentials, it contains tokens for refreshing your
	 * minecraft session that should be kept private!
	 * </p>
	 *
	 * @param outputStream Output stream to write the {@link AuthenticationFile} to
	 * @throws IOException Errors if output stream is not writable
	 */
	public void write(OutputStream outputStream) throws IOException {
		AuthenticationUtil.writeAuthenticationFile(this, outputStream);
	}
	
	/**
	 * Write this {@link AuthenticationFile} to a byte array.
	 * <p>
	 * Attention: The data is in plain text and can be read by anyone that has access to the byte array data (e.g. writing
	 * to a file). Even though this data does not contain any credentials, it contains tokens for refreshing your minecraft
	 * session that should be kept private!
	 * </p>
	 * 
	 * @return Bytes of the authentication file
	 */
	public byte[] write() {
		return AuthenticationUtil.writeAuthenticationFile(this);
	}
	
	public static class AuthenticationFileDeserializer implements JsonDeserializer<AuthenticationFile>, JsonSerializer<AuthenticationFile> {
		
		@Override
		public AuthenticationFile deserialize(JsonElement json, java.lang.reflect.Type typeOf, JsonDeserializationContext context) throws JsonParseException {
			final JsonObject object = json.getAsJsonObject();
			final Type type = context.deserialize(object.get("type"), Type.class);
			
			if (type == Type.MICROSOFT) {
				return MicrosoftAuthenticationFileDeserializer.INSTANCE.deserialize(json, typeOf, context);
			} else {
				throw new JsonParseException("Type must be 'microsoft'");
			}
		}
		
		@Override
		public JsonElement serialize(AuthenticationFile src, java.lang.reflect.Type typeOfSrc, JsonSerializationContext context) {
			if (src.type == Type.MICROSOFT && src instanceof MicrosoftAuthenticationFile microsoftSrc) {
				return MicrosoftAuthenticationFileDeserializer.INSTANCE.serialize(microsoftSrc, typeOfSrc, context);
			} else {
				throw new IllegalStateException("Type must be 'microsoft'");
			}
		}
	}
}
