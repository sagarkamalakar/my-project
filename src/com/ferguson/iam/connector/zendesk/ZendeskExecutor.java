package com.ferguson.iam.connector.zendesk;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import sailpoint.api.SailPointContext;
import sailpoint.integration.AbstractIntegrationExecutor;
import sailpoint.integration.IntegrationInterface;
import sailpoint.integration.ProvisioningPlan;
import sailpoint.integration.RequestResult;
import sailpoint.object.Identity;
import sailpoint.object.IntegrationConfig;
import sailpoint.object.ProvisioningResult;
import sailpoint.tools.Util;

public class ZendeskExecutor extends AbstractIntegrationExecutor implements IntegrationInterface {
	private static Log log = LogFactory.getLog("com.idmworks.iiq.ZendeskExecutor");
	private Map<String, Object> config;

	@Override
	public void configure(Map configMap) throws Exception {
		if (configMap != null)
			this.config = configMap;
	}

	@Override
	public void configure(SailPointContext context, IntegrationConfig config) throws Exception {
		super.configure(context, config);
	}

	@Override
	public RequestResult provision(Identity identity, ProvisioningPlan plan) throws Exception {

		log.debug("Method invoked: provision");
		RequestResult result = new RequestResult();
		ProvisioningPlan.AccountRequest accountRequest = (ProvisioningPlan.AccountRequest) plan.getAccountRequests()
				.get(0);
		log.debug("Operation:" + accountRequest.getOperation());
		Map attrMap = plan.getArguments();
		
		String grpName = (String) attrMap.get("groupname");
		String memeberUPN = (String) attrMap.get("memberupn");
		String comments = (String) attrMap.get("comments");
		log.debug("grpName::" + grpName);
		log.debug("memeberUPN::" + memeberUPN);
		log.debug("Comment::" + comments);

		String webHookEndPoint = (String) this.config.get("webHookURL");

		result = validateConfiguration();
		if (result.getErrors() == null) {
			log.debug("Plan XML=" + plan.toString());
			URL url = new URL(webHookEndPoint);

			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setRequestMethod("POST");
			con.setRequestProperty("Content-Type", "application/json; utf-8");
			con.setRequestProperty("Accept", "application/json");

			con.setDoOutput(true);
			// JSON String need to be constructed for the specific resource.
			// We may construct complex JSON using any third-party JSON
			// libraries such as jackson or org.json
			String jsonInputString = "{\"groupname\":\""+grpName+"\",\"memberupn\":\""+memeberUPN+"\",\"comments\":\""+comments+"\"}";


			try (OutputStream os = con.getOutputStream()) {
				byte[] input = jsonInputString.getBytes("utf-8");
				os.write(input, 0, input.length);
			}

			int code = con.getResponseCode();
			System.out.println(code);
			try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), "utf-8"))) {
				StringBuilder response = new StringBuilder();
				String responseLine = null;
				while ((responseLine = br.readLine()) != null) {
					response.append(responseLine.trim());
				}
				System.out.println(response.toString());
				Map<String, Object> responseMap = convertJsonToMap(response.toString());
				if (responseMap.get("status").toString().equalsIgnoreCase("success") && responseMap.get("status") != null) {
					result.setStatus("committed");
					//result.setRequestID((String) responseMap.get("request_id"));
					log.debug("WEBHOOK called Successfully::");
				} else {
					result.setStatus("failed");
					if (responseMap.get("errorMessage") != null)
						result.addError((String) responseMap.get("errorMessage"));
					else {
						result.addError("Failed to create Zendesk Ticket");
					}
				}
				log.debug("Ticket ID:" + responseMap.get("request_id"));
			}
		}

		return result;
	}

	private Map<String, Object> convertJsonToMap(String json) {

		Map<String, Object> map = new HashMap<String, Object>();
		ObjectMapper mapper = new ObjectMapper();
		try {

			// convert JSON string to Map
			map = mapper.readValue(json, new TypeReference<HashMap<String, Object>>() {
			});

		} catch (Exception e) {
			e.printStackTrace();
		}
		return map;
	}

	RequestResult validateConfiguration() {
		RequestResult result = new RequestResult();
		if (Util.isNullOrEmpty((String) this.config.get("webHookURL"))) {
			result.addError("Integration config error: Mandatory parameter webHookURL missing");
		}

		return result;
	}

}
