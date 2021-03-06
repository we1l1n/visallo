package org.visallo.web.auth.oauth;

import org.visallo.core.config.Configurable;

public class OAuthConfiguration {
    private String key;
    private String secret;

    public String getKey() {
        return key;
    }

    @Configurable(name = "key")
    public void setKey(String id) {
        this.key = id;
    }

    public String getSecret() {
        return secret;
    }

    @Configurable(name = "secret")
    public void setSecret(String secret) {
        this.secret = secret;
    }
}
