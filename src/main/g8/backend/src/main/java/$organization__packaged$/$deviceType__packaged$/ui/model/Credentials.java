package $organization;format="package"$.$deviceType;format="camel"$.ui.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Required for authentication
 */
public class Credentials {

    private final String username;
    private final String password;

    @JsonCreator
    public Credentials(
            @JsonProperty(value = "username", required = true) String username,
            @JsonProperty(value = "password", required = true) String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
