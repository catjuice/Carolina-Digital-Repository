/**
 * Copyright 2008 The University of North Carolina at Chapel Hill
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.unc.lib.dl.ui.service;

import java.io.IOException;
import java.io.OutputStream;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.unc.lib.dl.fedora.AccessClient;
import edu.unc.lib.dl.fedora.AuthorizationException;
import edu.unc.lib.dl.fedora.FedoraException;
import edu.unc.lib.dl.acl.util.GroupsThreadStore;
import edu.unc.lib.dl.fedora.PID;
import edu.unc.lib.dl.httpclient.HttpClientUtil;
import edu.unc.lib.dl.ui.exception.ResourceNotFoundException;
import edu.unc.lib.dl.ui.util.FedoraUtil;
import edu.unc.lib.dl.ui.util.FileIOUtil;
import edu.unc.lib.dl.util.ContentModelHelper;
import edu.unc.lib.dl.util.TripleStoreQueryService;

/**
 * Connects to and streams datastreams from Fedora.
 * 
 * @author bbpennel
 */
public class FedoraContentService {
	private static final Logger LOG = LoggerFactory.getLogger(FedoraContentService.class);

	private AccessClient accessClient;

	private TripleStoreQueryService tripleStoreQueryService;

	private FedoraUtil fedoraUtil;

	public void setAccessClient(edu.unc.lib.dl.fedora.AccessClient accessClient) {
		this.accessClient = accessClient;
	}

	public void setTripleStoreQueryService(TripleStoreQueryService tripleStoreQueryService) {
		this.tripleStoreQueryService = tripleStoreQueryService;
	}

	public void setFedoraUtil(FedoraUtil fedoraUtil) {
		this.fedoraUtil = fedoraUtil;
	}

	public void streamData(String simplepid, String datastream, OutputStream outStream) throws FedoraException {
		streamData(simplepid, datastream, outStream, null, null, true);
	}

	public void streamData(String simplepid, String datastream, OutputStream outStream, HttpServletResponse response,
			String fileExtension, boolean asAttachment) throws FedoraException {
		this.streamData(simplepid, datastream, outStream, response, fileExtension, asAttachment, 1);
	}

	public void streamData(String simplepid, String datastream, OutputStream outStream, HttpServletResponse response,
			String fileExtension, boolean asAttachment, int retryServerError) throws FedoraException {
		String dataUrl = fedoraUtil.getFedoraUrl() + "/objects/" + simplepid + "/datastreams/" + datastream + "/content";

		HttpClient client = HttpClientUtil.getAuthenticatedClient(dataUrl, accessClient.getUsername(),
				accessClient.getPassword());
		client.getParams().setAuthenticationPreemptive(true);
		GetMethod method = new GetMethod(dataUrl);
		method.addRequestHeader(HttpClientUtil.FORWARDED_GROUPS_HEADER, GroupsThreadStore.getGroupString());

		try {
			client.executeMethod(method);
		} catch (HttpException e) {
			LOG.error("Error while attempting to stream Fedora content for " + simplepid, e);
		} catch (IOException e) {
			LOG.error("Error while attempting to stream Fedora content for " + simplepid, e);
		}
		if (method.getStatusCode() == HttpStatus.SC_OK) {
			if (response != null) {
				PID pid = new PID(simplepid);

				String mimeType = null;
				if (fileExtension != null && fileExtension.equals("mp3")) {
					mimeType = "audio/mpeg";
				}
				if (mimeType == null && datastream.equals(ContentModelHelper.Datastream.DATA_FILE.getName())) {
					mimeType = tripleStoreQueryService.lookupSourceMimeType(pid);
				}
				if (mimeType == null) {
					response.setHeader("Content-Type", method.getResponseHeader("content-type").getValue());
				} else {
					response.setHeader("Content-Type", mimeType);
				}

				String slug = null;
				try {
					slug = tripleStoreQueryService.lookupSlug(pid);
					if (slug != null) {
						if (fileExtension != null && !slug.toLowerCase().contains("." + fileExtension.toLowerCase())) {
							slug += "." + fileExtension;
						}
					}
				} catch (Exception e) {
					LOG.error("Error while attempting to retrieve slug for " + simplepid, e);
				}

				if (asAttachment) {
					response.setHeader("content-disposition", "attachment; filename=\"" + slug + "\"");
				} else {
					response.setHeader("content-disposition", "inline; filename=\"" + slug + "\"");
				}
			}

			try {
				FileIOUtil.stream(outStream, method);
			} catch (IOException e) {
				LOG.warn("Problem retrieving " + dataUrl + " for " + simplepid + ": " + e.getMessage());
			} finally {
				method.releaseConnection();
			}
		} else if (method.getStatusCode() == HttpStatus.SC_FORBIDDEN) {
			throw new AuthorizationException("User does not have sufficient permissions to retrieve the specified object");
		} else {
			// Retry server errors
			if (method.getStatusCode() == 500 && retryServerError > 0) {
				LOG.warn("Failed to retrieve " + dataUrl + ", retrying.");
				this.streamData(simplepid, datastream, outStream, response, fileExtension, asAttachment,
						retryServerError - 1);
			} else {
				throw new ResourceNotFoundException("Failure to fedora content due to response of: "
						+ method.getStatusLine().toString() + "\nPath was: " + dataUrl);
			}
		}
	}
}