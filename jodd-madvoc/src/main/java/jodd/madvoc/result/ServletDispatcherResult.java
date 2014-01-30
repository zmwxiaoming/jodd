// Copyright (c) 2003-2014, Jodd Team (jodd.org). All Rights Reserved.

package jodd.madvoc.result;

import jodd.madvoc.ActionRequest;
import jodd.madvoc.MadvocUtil;
import jodd.madvoc.ResultPath;
import jodd.madvoc.ScopeType;
import jodd.madvoc.component.ResultMapper;
import jodd.madvoc.meta.In;
import jodd.servlet.DispatcherUtil;
import jodd.util.StringPool;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import java.net.MalformedURLException;
import java.util.HashMap;

/**
 * Dispatcher.
 * 
 * @see ServletRedirectResult
 */
public class ServletDispatcherResult extends BaseActionResult<String> {

	public static final String NAME = "dispatch";
	protected static final String EXTENSION = ".jsp";
	protected HashMap<String, String> targetCache;

	public ServletDispatcherResult() {
		super(NAME);
		targetCache = new HashMap<String, String>(256);
	}

	@In(scope = ScopeType.CONTEXT)
	protected ResultMapper resultMapper;

	/**
	 * Dispatches to the JSP location created from result value and JSP extension.
	 * Does its forward via a <code>RequestDispatcher</code>. If the dispatch fails, a 404 error
	 * will be sent back in the http response.
	 */
	public void render(ActionRequest actionRequest, String resultValue) throws Exception {
		String actionAndResultPath = actionRequest.getActionPath() + (resultValue != null ? ' ' + resultValue : StringPool.EMPTY);
		String target = targetCache.get(actionAndResultPath);

		HttpServletRequest request = actionRequest.getHttpServletRequest();
		HttpServletResponse response = actionRequest.getHttpServletResponse();

		if (target == null) {
			ResultPath resultPath = resultMapper.resolveResultPath(actionRequest.getActionPath(), resultValue);

			ServletContext servletContext = request.getSession().getServletContext();

			String path = resultPath.getPath();
			String value = resultPath.getValue();

			while (true) {
				// variant #1: with value
				if (path == null) {
					target = value + EXTENSION;
				} else {
					target = path + '.' + value + EXTENSION;
				}

				if (targetExist(servletContext, target)) {
					break;
				}

				// variant #1: without value

				if (path != null) {
					target = path + EXTENSION;

					if (targetExist(servletContext, target)) {
						break;
					}
				}

				// continue

				if (path == null) {
					response.sendError(SC_NOT_FOUND, "Result not found: " + resultPath);
					return;
				}

				int dotNdx = MadvocUtil.lastIndexOfDotAfterSlash(path);
				if (dotNdx == -1) {
					path = null;
				} else {
					path = path.substring(0, dotNdx);
				}
			}

			// store target in cache
			targetCache.put(actionAndResultPath, target);
		}

		// the target exists

		RequestDispatcher dispatcher = request.getRequestDispatcher(target);
		if (dispatcher == null) {
			response.sendError(SC_NOT_FOUND, "Result not found: " + target);	// should never happened
			return;
		}

		// If we're included, then include the view, otherwise do forward.
		// This allow the page to, for example, set content type.

		if (DispatcherUtil.isPageIncluded(request, response)) {
			dispatcher.include(request, response);
		} else {
			dispatcher.forward(request, response);
		}
	}

	/**
	 * Returns <code>true</code> if target exists. Results are cached for performances.
	 */
	protected boolean targetExist(ServletContext servletContext, String target) {
		try {
			return servletContext.getResource(target) != null;
		} catch (MalformedURLException ignore) {
			return false;
		}
	}

}