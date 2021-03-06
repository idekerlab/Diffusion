package org.cytoscape.diffusion.internal.rest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.cytoscape.application.CyApplicationManager;


import org.cytoscape.ci.model.CIError;
import org.cytoscape.ci.model.CIResponse;

import org.cytoscape.diffusion.internal.DiffusionDocumentation;

import org.cytoscape.ci.CIErrorFactory;
import org.cytoscape.ci.CIExceptionFactory;
import org.cytoscape.ci.CIResponseFactory;
import org.cytoscape.diffusion.internal.client.DiffusionServiceException;

import org.cytoscape.diffusion.internal.task.DiffusionContextMenuTaskFactory;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.work.SynchronousTaskManager;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.work.TaskObserver;

import org.cytoscape.work.util.ListSingleSelection;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

@Api(tags = {"Apps: Diffusion"})
@Path("/diffusion/v1/")
public class DiffusionResource {

	private final CyApplicationManager cyApplicationManager;
	private final SynchronousTaskManager<Object> taskManager;
	
	private final CyNetworkManager cyNetworkManager;
	private final CyNetworkViewManager cyNetworkViewManager;
	
	private final DiffusionContextMenuTaskFactory diffusionTaskFactory;
	private final DiffusionContextMenuTaskFactory diffusionWithOptionsTaskFactory;
	
	
	private final ServiceTracker ciResponseFactoryTracker;
	
	private CIResponseFactory getCIResponseFactory() {
		return (CIResponseFactory) ciResponseFactoryTracker.getService();
	}
	//private final CIResponseFactory ciResponseFactory;
	
	private final ServiceTracker ciExceptionFactoryTracker;
	private CIExceptionFactory getCIExceptionFactory() {
		return (CIExceptionFactory) ciExceptionFactoryTracker.getService();
	}
	//private final CIExceptionFactory ciExceptionFactory;
	
	private final ServiceTracker ciErrorFactoryTracker;
	private CIErrorFactory getCIErrorFactory() {
		return (CIErrorFactory) ciErrorFactoryTracker.getService();
	}
	//private final CIErrorFactory ciErrorFactory;
	
	public static final String POST = "POST";
	
	public static final String CY_NETWORK_NOT_FOUND_CODE = "1";
	public static final String CY_NETWORK_VIEW_NOT_FOUND_CODE = "2";
	public static final String TASK_EXECUTION_ERROR_CODE= "3";

	public DiffusionResource(final CyApplicationManager cyApplicationManager, final SynchronousTaskManager<Object> taskManager, final CyNetworkManager cyNetworkManager, final CyNetworkViewManager cyNetworkViewManager, final DiffusionContextMenuTaskFactory diffusionTaskFactory, final DiffusionContextMenuTaskFactory diffusionWithOptionsTaskFactory, final ServiceTracker ciResponseFactoryTracker, final ServiceTracker ciExceptionFactoryTracker, final ServiceTracker ciErrorFactoryTracker) {
		this.cyApplicationManager = cyApplicationManager;
		this.taskManager = taskManager;
		this.cyNetworkManager = cyNetworkManager;
		this.cyNetworkViewManager = cyNetworkViewManager;
		this.diffusionTaskFactory = diffusionTaskFactory;
		this.diffusionWithOptionsTaskFactory = diffusionWithOptionsTaskFactory;
		this.ciResponseFactoryTracker = ciResponseFactoryTracker;
		this.ciExceptionFactoryTracker = ciExceptionFactoryTracker;
		this.ciErrorFactoryTracker = ciErrorFactoryTracker;
		
	}

	private static final Logger logger = LoggerFactory.getLogger(DiffusionResource.class);
	
	private static final RemoteLogger remoteLogger = RemoteLogger.getDefaultLogger();
	
	private final static String resourceErrorRoot = "urn:cytoscape:ci:diffusion-app:v1";


	private CIError buildCIError(int status, String resourcePath, String code, String message, Exception e) {
		String errorUrn = resourceErrorRoot + ":" + resourcePath+ ":"+ code;
		remoteLogger.logResourceError("POST", resourcePath, status, errorUrn);
		return getCIErrorFactory().getCIError(status, resourceErrorRoot + ":" + resourcePath+ ":"+ code, message);
	}
	
	CIResponse<Object> buildCIErrorResponse(int status, String resourcePath, String code, String message, Exception e)
	{
		CIResponse<Object> response = getCIResponseFactory().getCIResponse(new Object());
		CIError error = buildCIError(status, resourcePath, code, message, e);

		if (e != null)
		{
			logger.error(message, e);
			if (e instanceof DiffusionServiceException) {
				response.errors.addAll(((DiffusionServiceException)e).getCIErrors());
			}
		}
		else
		{
			logger.error(message);
		}
	
		response.errors.add(error);
		return response;
	}

	public CyNetwork getCyNetwork(String resourcePath, String errorType)
	{
		CyNetwork cyNetwork = cyApplicationManager.getCurrentNetwork();
		
		if (cyNetwork == null) {
			String messageString = "Could not find current Network";
			throw getCIExceptionFactory().getCIException(404, new CIError[]{this.buildCIError(404, resourcePath, errorType, messageString, null)});		
		}
		return cyNetwork;
	}
		
	public CyNetworkView getCyNetworkView(String resourcePath, String errorType) {
		CyNetworkView cyNetworkView = cyApplicationManager.getCurrentNetworkView();
		if (cyNetworkView == null) {
			String messageString = "Could not find current Network View";
			throw getCIExceptionFactory().getCIException(404, new CIError[]{this.buildCIError(404, resourcePath, errorType, messageString, null)});		
		}
		return cyNetworkView;
	}
	
	public CyNetworkView getCyNetworkView(String resourcePath, String errorType, long networkSUID, long networkViewSUID)
	{
		final CyNetwork network = cyNetworkManager.getNetwork(networkSUID);
		if (network == null) {
			String messageString = "Could not find network with SUID: " + networkSUID;
			throw getCIExceptionFactory().getCIException(404, new CIError[]{this.buildCIError(404, resourcePath, errorType, messageString, null)});		
		}
		final Collection<CyNetworkView> views = cyNetworkViewManager.getNetworkViews(network);
		if (views.isEmpty()) {
			String messageString = "No views are available for network with SUID: " + networkSUID;
			throw getCIExceptionFactory().getCIException(404, new CIError[]{this.buildCIError(404, resourcePath, errorType, messageString, null)});		
		}
		for (final CyNetworkView view : views) {
			final Long vid = view.getSUID();
			if (vid.equals(networkViewSUID)) {
				return view;
			}
		}
		String messageString = "Could not find network view with SUID: " + networkViewSUID + " for network with SUID: " + networkSUID;
		throw getCIExceptionFactory().getCIException(404, new CIError[]{this.buildCIError(404, resourcePath, errorType, messageString, null)});		
	}
	
	@ApiModel(value="Diffusion App Response", description="Diffusion Analysis Results in CI Format", parent=CIResponse.class)
	public static class DiffusionAppResponse extends CIResponse<DiffusionResultColumns>{

	}
	
	@POST
	@Produces("application/json")
	@Consumes("application/json")
	@Path("currentView/diffuse_with_options")
	@ApiOperation(value = "Execute Diffusion Analysis on Current View with Options",
	notes = DiffusionDocumentation.GENERIC_SWAGGER_NOTES,
	response = DiffusionAppResponse.class)
	@ApiResponses(value = { 
			@ApiResponse(code = 404, message = "Network or Network View does not exist", response = CIResponse.class)
	})
	public Response diffuseWithOptions(@ApiParam(value = "Diffusion Parameters", required = true) DiffusionParameters diffusionParameters)
	{
		CyNetwork cyNetwork = getCyNetwork("diffuse_current_view_with_options", CY_NETWORK_NOT_FOUND_CODE);
		CyNetworkView cyNetworkView = getCyNetworkView("diffuse_current_view_with_options", CY_NETWORK_VIEW_NOT_FOUND_CODE);
		Response response =  diffuseWithOptions(cyNetwork.getSUID(), cyNetworkView.getSUID(), diffusionParameters);
		remoteLogger.logResourceResponse("POST", "/diffusion/v1/currentView/diffuse_with_options", response.getStatus());
		return response;
	}
	
	private void logCIResponse(String httpMethod, String path, CIResponse<?> response) {
		try {
			if (response.errors.size() == 0) {
				remoteLogger.logResourceResponse(httpMethod, path, Response.Status.OK.getStatusCode());
			}  else {
				for (CIError error : response.errors) {
					remoteLogger.logResourceError(httpMethod, path, Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), error.type);
				}
			}
		} catch (Throwable e) {
			logger.error("Remote logging response parse failed", e);
		}
	}
	
	@POST
	@Produces("application/json")
	@Consumes("application/json")
	@Path("{networkSUID}/views/{networkViewSUID}/diffuse_with_options")
	@ApiOperation(value = "Execute Diffusion Analysis on a Specific Network View with Options",
	notes = DiffusionDocumentation.GENERIC_SWAGGER_NOTES,
	response = DiffusionAppResponse.class)
	@ApiResponses(value = { 
			@ApiResponse(code = 404, message = "Network does not exist", response = CIResponse.class)
	})
	public Response diffuseWithOptions(@ApiParam(value="Network SUID (see GET /v1/networks)") @PathParam("networkSUID") long networkSUID, @ApiParam(value="Network View SUID (see GET /v1/networks/{networkId}/views)") @PathParam("networkViewSUID") long networkViewSUID, @ApiParam(value = "Diffusion Parameters", required = true) DiffusionParameters diffusionParameters) {

		//System.out.println("Accessing Diffusion with options via REST");
		CyNetworkView cyNetworkView = getCyNetworkView("diffuse_with_options", CY_NETWORK_VIEW_NOT_FOUND_CODE, networkSUID, networkViewSUID);
		DiffusionTaskObserver taskObserver = new DiffusionTaskObserver(this, "diffuse_with_options", TASK_EXECUTION_ERROR_CODE);
		
		Map<String, Object> tunableMap = new HashMap<String, Object>();
		
		//This next section is VERY interesting. Since we're accessing DiffusionWithOptionsTaskFactory without the
		//benefit of interceptors or CommandExecutor, we have the option of literally building tunables from scratch.
		ListSingleSelection<String> heatColumnName = new ListSingleSelection<String>();
		List<String> heatColumns = new ArrayList<String>();
		heatColumns.add(diffusionParameters.heatColumnName);
		heatColumnName.setPossibleValues(heatColumns);
		heatColumnName.setSelectedValue(diffusionParameters.heatColumnName);
		
		tunableMap.put("heatColumnName", heatColumnName);
		tunableMap.put("time", diffusionParameters.time);
		TaskIterator taskIterator = diffusionWithOptionsTaskFactory.createTaskIterator(cyNetworkView);
		taskManager.setExecutionContext(tunableMap);
		taskManager.execute(taskIterator, taskObserver);
		
		logCIResponse("POST", "/diffusion/v1/{networkSUID}/views/{networkViewSUID}/diffuse_with_options", taskObserver.response);
		
		return Response.status(taskObserver.response.errors.size() == 0 ? Response.Status.OK : Response.Status.INTERNAL_SERVER_ERROR)
				.type(MediaType.APPLICATION_JSON)
				.entity(taskObserver.response).build();
	}


	

	@POST
	@Produces("application/json")
	@Consumes("application/json")
	@Path("currentView/diffuse")
	@ApiOperation(value = "Execute Diffusion Analysis on Current View",
	notes = DiffusionDocumentation.GENERIC_SWAGGER_NOTES + DiffusionDocumentation.ADDITIONAL_SELECTION_SWAGGER_NOTES,
	response = DiffusionAppResponse.class)
	@ApiResponses(value = { 
			@ApiResponse(code = 404, message = "Network or Network View does not exist", response = CIResponse.class)
	})
	public Response diffuse()
	{
		CyNetwork cyNetwork = getCyNetwork("diffuse_current_view", CY_NETWORK_NOT_FOUND_CODE);
		CyNetworkView cyNetworkView = getCyNetworkView("diffuse_current_view", CY_NETWORK_VIEW_NOT_FOUND_CODE);
		Response response = diffuse(cyNetwork.getSUID(), cyNetworkView.getSUID());
		remoteLogger.logResourceResponse("POST", "/diffusion/v1/currentView/diffuse", response.getStatus());
		return response;
	}
	
	
	@POST
	@Produces("application/json")
	@Consumes("application/json")
	@Path("{networkSUID}/views/{networkViewSUID}/diffuse")
	@ApiOperation(value = "Execute Diffusion Analysis on a Specific Network View",
	notes = DiffusionDocumentation.GENERIC_SWAGGER_NOTES + DiffusionDocumentation.ADDITIONAL_SELECTION_SWAGGER_NOTES,
	response = DiffusionAppResponse.class)

	@ApiResponses(value = { 
			@ApiResponse(code = 404, message = "Network does not exist", response = CIResponse.class),
	})

	public Response diffuse(@ApiParam(value="Network SUID (see GET /v1/networks)") @PathParam("networkSUID") long networkSUID, @ApiParam(value="Network View SUID (see GET /v1/networks/{networkId}/views)") @PathParam("networkViewSUID") long networkViewSUID) {

		System.out.println("Accessing Diffusion via REST");
		DiffusionTaskObserver taskObserver = new DiffusionTaskObserver(this, "diffuse", TASK_EXECUTION_ERROR_CODE);
	
		executeDiffuse(networkSUID, networkViewSUID, taskObserver);
		
		logCIResponse("POST", "/diffusion/v1/{networkSUID}/views/{networkViewSUID}/diffuse", taskObserver.response);
		
		return Response.status(taskObserver.response.errors.size() == 0 ? Response.Status.OK : Response.Status.INTERNAL_SERVER_ERROR)
				.type(MediaType.APPLICATION_JSON)
				.entity(taskObserver.response).build();

	}
	
	public void executeDiffuse(long networkSUID, long networkViewSUID, TaskObserver taskObserver) {
		CyNetworkView cyNetworkView = getCyNetworkView("diffuse", CY_NETWORK_VIEW_NOT_FOUND_CODE, networkSUID, networkViewSUID);
		TaskIterator taskIterator = diffusionTaskFactory.createTaskIterator(cyNetworkView);
		taskManager.execute(taskIterator, taskObserver);
	}
}
