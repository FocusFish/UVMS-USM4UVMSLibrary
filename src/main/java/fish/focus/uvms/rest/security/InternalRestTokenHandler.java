package fish.focus.uvms.rest.security;

import java.time.Instant;
import java.util.Collections;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import fish.focus.uvms.usm.jwt.DefaultJwtTokenHandler;
import fish.focus.uvms.usm.jwt.JwtTokenHandler;

@RequestScoped
public class InternalRestTokenHandler {

    @Inject
    private JwtTokenHandler handler;

    private String token;
    private Instant validTo;

    public String createAndFetchToken(String username) {
        if(token != null && isValid()) {
            return token;
        } else {
            validTo = Instant.now().plusMillis(DefaultJwtTokenHandler.DEFAULT_TTL);
            token = handler.createToken(username,
                    Collections.singletonList(UnionVMSFeature.manageInternalRest.getFeatureId()));
            return token;
        }
    }

    private boolean isValid() {
        return this.validTo.isAfter(Instant.now());
    }
}
