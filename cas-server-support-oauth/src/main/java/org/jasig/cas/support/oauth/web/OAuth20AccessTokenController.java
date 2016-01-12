package org.jasig.cas.support.oauth.web;

import org.apache.http.HttpStatus;
import org.jasig.cas.authentication.Authentication;
import org.jasig.cas.authentication.principal.Service;
import org.jasig.cas.support.oauth.OAuthConstants;
import org.jasig.cas.support.oauth.OAuthUtils;
import org.jasig.cas.support.oauth.ticket.accesstoken.AccessToken;
import org.jasig.cas.support.oauth.ticket.accesstoken.AccessTokenFactory;
import org.jasig.cas.support.oauth.ticket.code.OAuthCodeImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotNull;

/**
 * This controller returns an access token according to the given OAuth code.
 *
 * @author Jerome Leleu
 * @since 3.5.0
 */
@Component("accessTokenController")
public final class OAuth20AccessTokenController extends BaseOAuthWrapperController {

    @Autowired
    @Qualifier("defaultAccessTokenFactory")
    private AccessTokenFactory accessTokenFactory;

    @NotNull
    @Value("${tgt.timeToKillInSeconds:7200}")
    protected long timeout;

    protected ModelAndView handleRequestInternal(final HttpServletRequest request, final HttpServletResponse response) throws Exception {

        if (!verifyAccessTokenRequest(request)) {
            return OAuthUtils.writeTextError(response, OAuthConstants.INVALID_REQUEST, HttpStatus.SC_BAD_REQUEST);
        }

        final String codeParameter = request.getParameter(OAuthConstants.CODE);
        final OAuthCodeImpl code = (OAuthCodeImpl) ticketRegistry.getTicket(codeParameter);
        // service ticket should be valid
        if (code == null || code.isExpired()) {
            logger.error("Code expired: {}", code);
            return OAuthUtils.writeTextError(response, OAuthConstants.INVALID_GRANT, HttpStatus.SC_BAD_REQUEST);
        }
        ticketRegistry.deleteTicket(code.getId());

        final Service service = code.getService();
        final Authentication authentication = code.getAuthentication();
        final AccessToken accessToken = accessTokenFactory.create(service, authentication);
        ticketRegistry.addTicket(accessToken);

        final String text = String.format("%s=%s&%s=%s", OAuthConstants.ACCESS_TOKEN, accessToken.getId(), OAuthConstants.EXPIRES, timeout);
        logger.debug("OAuth access token response: {}", text);
        response.setContentType("text/plain");
        return OAuthUtils.writeText(response, text, HttpStatus.SC_OK);
    }

    /**
     * Verify the access token request.
     *
     * @param request the HTTP request
     * @return true, if successful
     */
    private boolean verifyAccessTokenRequest(final HttpServletRequest request) {

        return checkParameterExist(request, OAuthConstants.CLIENT_ID)
            && checkParameterExist(request, OAuthConstants.REDIRECT_URI)
            && checkParameterExist(request, OAuthConstants.CLIENT_SECRET)
            && checkParameterExist(request, OAuthConstants.CODE)
            && checkServiceValid(request)
            && checkCallbackValid(request)
            && checkClientSecret(request);
    }

    public AccessTokenFactory getAccessTokenFactory() {
        return accessTokenFactory;
    }

    public void setAccessTokenFactory(AccessTokenFactory accessTokenFactory) {
        this.accessTokenFactory = accessTokenFactory;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }
}
