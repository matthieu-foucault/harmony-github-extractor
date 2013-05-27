package fr.labri.harmony.extractor.github;

import java.util.Collection;
import java.util.Iterator;

import fr.labri.harmony.core.config.model.SourceConfiguration;

public class OAuthTokensManager {

	private final static String GITHUB_OAUTH_TOKENS = "oauth-tokens";
	private Collection<String> oAuthTokens;
	private Iterator<String> oAuthTokensIterator;

	@SuppressWarnings("unchecked")
	public OAuthTokensManager(SourceConfiguration config) {
		oAuthTokens = (Collection<String>) config.getOptions().get(GITHUB_OAUTH_TOKENS);
		if (oAuthTokens == null || oAuthTokens.isEmpty()) throw new IllegalArgumentException("The oauth-tokens array cannot be null or empty. Check your source configuration");
		oAuthTokensIterator = oAuthTokens.iterator();

	}

	public String nextToken() {
		if (!oAuthTokensIterator.hasNext()) oAuthTokensIterator = oAuthTokens.iterator();

		return oAuthTokensIterator.next();
	}

}
