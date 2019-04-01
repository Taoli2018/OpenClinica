package org.akaza.openclinica.controller;

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.ApiParam;

import org.akaza.openclinica.bean.core.SubjectEventStatus;
import org.akaza.openclinica.bean.login.RestReponseDTO;
import org.akaza.openclinica.bean.login.ResponseSuccessStudyParticipantDTO;
import org.akaza.openclinica.bean.login.UserAccountBean;
import org.akaza.openclinica.bean.managestudy.StudyBean;
import org.akaza.openclinica.bean.managestudy.StudyEventBean;
import org.akaza.openclinica.bean.managestudy.StudyEventDefinitionBean;
import org.akaza.openclinica.bean.managestudy.StudySubjectBean;
import org.akaza.openclinica.controller.dto.StudyEventScheduleDTO;
import org.akaza.openclinica.controller.dto.StudyEventScheduleRequestDTO;
import org.akaza.openclinica.controller.helper.RestfulServiceHelper;
import org.akaza.openclinica.dao.core.CoreResources;
import org.akaza.openclinica.dao.hibernate.EventCrfDao;
import org.akaza.openclinica.dao.hibernate.EventDefinitionCrfDao;
import org.akaza.openclinica.dao.hibernate.StudyEventDao;
import org.akaza.openclinica.dao.hibernate.StudyEventDefinitionDao;
import org.akaza.openclinica.dao.hibernate.StudyParameterValueDao;
import org.akaza.openclinica.dao.hibernate.StudySubjectDao;
import org.akaza.openclinica.dao.login.UserAccountDAO;
import org.akaza.openclinica.dao.managestudy.StudySubjectDAO;
import org.akaza.openclinica.dao.managestudy.StudyDAO;
import org.akaza.openclinica.dao.managestudy.StudyEventDAO;
import org.akaza.openclinica.dao.managestudy.StudyEventDefinitionDAO;
import org.akaza.openclinica.bean.core.Status;
import org.akaza.openclinica.domain.datamap.EventCrf;
import org.akaza.openclinica.domain.datamap.EventDefinitionCrf;
import org.akaza.openclinica.domain.datamap.Study;
import org.akaza.openclinica.domain.datamap.StudyEvent;
import org.akaza.openclinica.domain.datamap.StudyEventDefinition;
import org.akaza.openclinica.domain.datamap.StudyParameterValue;
import org.akaza.openclinica.domain.datamap.StudySubject;
import org.akaza.openclinica.exception.OpenClinicaException;
import org.akaza.openclinica.exception.OpenClinicaSystemException;
import org.akaza.openclinica.i18n.util.ResourceBundleProvider;
import org.akaza.openclinica.patterns.ocobserver.StudyEventChangeDetails;
import org.akaza.openclinica.patterns.ocobserver.StudyEventContainer;
import org.akaza.openclinica.service.CustomRuntimeException;
import org.akaza.openclinica.service.ParticipateService;
import org.akaza.openclinica.service.StudyEventService;
import org.akaza.openclinica.service.crfdata.ErrorObj;
import org.akaza.openclinica.service.pmanage.ParticipantPortalRegistrar;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.multipart.MultipartFile;


@Controller
@RequestMapping( value = "/auth/api" )
@Api( value = "Study Event", tags = {"Study Event"}, description = "REST API for Study Event" )
public class StudyEventController {

    @Autowired
    private ParticipateService participateService;

    @Autowired
    @Qualifier( "dataSource" )
    private BasicDataSource dataSource;

    @Autowired
    private EventCrfDao eventCrfDao;

    @Autowired
    private StudyEventDao studyEventDao;

    @Autowired
    private StudySubjectDao studySubjectDao;

    @Autowired
    private StudyEventDefinitionDao studyEventDefinitionDao;

    @Autowired
    private EventDefinitionCrfDao eventDefinitionCrfDao;
    private RestfulServiceHelper restfulServiceHelper;

    @Autowired
	private StudyEventService studyEventService;
	
    PassiveExpiringMap<String, Future<ResponseEntity<Object>>> expiringMap =
            new PassiveExpiringMap<>(24, TimeUnit.HOURS);
    
    protected final Logger logger = LoggerFactory.getLogger(getClass().getName());

    /**
     * @api {put} /pages/auth/api/v1/studyevent/studysubject/{studySubjectOid}/studyevent/{studyEventDefOid}/ordinal/{ordinal}/complete Complete a Participant Event
     * @apiName completeParticipantEvent
     * @apiPermission Authenticate using api-key. admin
     * @apiVersion 1.0.0
     * @apiParam {String} studySubjectOid Study Subject OID.
     * @apiParam {String} studyEventDefOid Study Event Definition OID.
     * @apiParam {Integer} ordinal Ordinal of Study Event Repetition.
     * @apiGroup Form
     * @apiHeader {String} api_key Users unique access-key.
     * @apiDescription Completes a participant study event.
     * @apiErrorExample {json} Error-Response:
     * HTTP/1.1 403 Forbidden
     * {
     * "code": "403",
     * "message": "Request Denied.  Operation not allowed."
     * }
     * @apiSuccessExample {json} Success-Response:
     * HTTP/1.1 200 OK
     * {
     * "code": "200",
     * "message": "Success."
     * }
     */
    @RequestMapping( value = "/studyevent/{studyEventDefOid}/ordinal/{ordinal}/complete", method = RequestMethod.PUT )
    public @ResponseBody
    Map<String, String> completeParticipantEvent(HttpServletRequest request, @PathVariable( "studyEventDefOid" ) String studyEventDefOid,
                                                 @PathVariable( "ordinal" ) Integer ordinal)
            throws Exception {
        String studyOid=(String)request.getSession().getAttribute("studyOid");
        UserAccountBean ub =(UserAccountBean) request.getSession().getAttribute("userBean");

        getRestfulServiceHelper().setSchema(studyOid, request);
        ResourceBundleProvider.updateLocale(new Locale("en_US"));
        if(ub==null){
            logger.info("userAccount is null");
            return null;
        }
        StudyBean currentStudy = participateService.getStudy(studyOid);
        StudySubjectDAO studySubjectDAO = new StudySubjectDAO(dataSource);

        String userName=ub.getName();
        int lastIndexOfDot= userName.lastIndexOf(".");
        String subjectOid=userName.substring(lastIndexOfDot+1);
        StudySubjectBean studySubject= studySubjectDAO.findByOid(subjectOid);

        StudyEvent studyEvent = studyEventDao.fetchByStudyEventDefOIDAndOrdinal(studyEventDefOid, ordinal, studySubject.getId());
        StudyEventDefinition studyEventDefinition = studyEventDefinitionDao.findByStudyEventDefinitionId(studyEvent.getStudyEventDefinition().getStudyEventDefinitionId());
        Study study = studyEventDefinition.getStudy();
        Map<String, String> response = new HashMap<String, String>();

        // Verify this request is allowed.
        if (!participateService.mayProceed(study.getOc_oid())) {
            response.put("code", String.valueOf(HttpStatus.FORBIDDEN.value()));
            response.put("message", "Request Denied.  Operation not allowed.");
            return response;
        }

        // Get list of eventCRFs
        // By this point we can assume all Participant forms have been submitted at least once and have an event_crf entry.
        // Non-Participant forms may not have an entry.
        List<EventDefinitionCrf> eventDefCrfs = eventDefinitionCrfDao.findByStudyEventDefinitionId(studyEventDefinition.getStudyEventDefinitionId());
        List<EventCrf> eventCrfs = eventCrfDao.findByStudyEventIdStudySubjectId(studyEvent.getStudyEventId(), studySubject.getOid());


        try {
            participateService.completeData(studyEvent, eventDefCrfs, eventCrfs);
        } catch (Exception e) {
            // Transaction has been rolled back due to an exception.
            logger.error("Error encountered while completing Study Event: " + e.getMessage());
            logger.error(ExceptionUtils.getStackTrace(e));

            response.put("code", String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()));
            response.put("message", "Error encountered while completing participant event.");
            return response;

        }


        response.put("code", String.valueOf(HttpStatus.OK.value()));
        response.put("message", "Success.");
        return response;
        //return new ResponseEntity<String>("<message>Success</message>", org.springframework.http.HttpStatus.OK);

    }
    public RestfulServiceHelper getRestfulServiceHelper() {
        if (restfulServiceHelper == null) {
            restfulServiceHelper = new RestfulServiceHelper(this.dataSource);
        }
        return restfulServiceHelper;
    }

    @ApiOperation(value = "To schedule an event for participant at site level",  notes = "Will read the information of SudyOID,ParticipantID, StudyEventOID, Ordinal, Start Date, End Date")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful operation"),
            @ApiResponse(code = 400, message = "Bad Request -- Normally means Found validation errors, for detail please see the error list: <br /> ")})
    @RequestMapping(value = "clinicaldata/studies/{studyOID}/sites/{siteOID}/participants/{subjectKey}/events", method = RequestMethod.POST)
    public ResponseEntity<Object> scheduleEventAtSiteLevel(HttpServletRequest request,
            @RequestBody StudyEventScheduleRequestDTO studyEventScheduleRequestDTO,
            @PathVariable("subjectKey") String subjectKey,
            @PathVariable("studyOID") String studyOID,
            @PathVariable("siteOID") String siteOID) throws Exception {
        
        String studyEventOID = studyEventScheduleRequestDTO.getStudyEventOID();
        String ordinal = studyEventScheduleRequestDTO.getOrdinal();
        String startDate = studyEventScheduleRequestDTO.getStartDate();
        String endDate = studyEventScheduleRequestDTO.getEndDate();
        
    	
    	return scheduleEvent(request, studyOID, siteOID,studyEventOID,subjectKey,ordinal,startDate,endDate);
	}
    
    @ApiOperation(value = "To schedule an event for participant at study level",  notes = "Will read the information of SudyOID,ParticipantID, StudyEventOID, Ordinal, Start Date, End Date")
   	@ApiResponses(value = {
   	        @ApiResponse(code = 200, message = "Successful operation"),
   	        @ApiResponse(code = 400, message = "Bad Request -- Normally means Found validation errors, for detail please see the error list: <br /> ")})
   	@RequestMapping(value = "clinicaldata/studies/{studyOID}/participants/{subjectKey}/events", method = RequestMethod.POST)
   	public ResponseEntity<Object> scheduleEventAtStudyLevel(HttpServletRequest request,
   			@RequestBody StudyEventScheduleRequestDTO studyEventScheduleRequestDTO,
   			@PathVariable("subjectKey") String subjectKey,									
   			@PathVariable("studyOID") String studyOID) throws Exception {
   		
       	String studyEventOID = studyEventScheduleRequestDTO.getStudyEventOID();
       	String ordinal = studyEventScheduleRequestDTO.getOrdinal();
       	String startDate = studyEventScheduleRequestDTO.getStartDate();
       	String endDate = studyEventScheduleRequestDTO.getEndDate();
		
    	return scheduleEvent(request, studyOID, null,studyEventOID,subjectKey,ordinal,startDate,endDate);
	}
    
    @ApiOperation(value = "To schedule an event for participants at site level in bulk",  notes = "Will read the information of SudyOID,ParticipantID, StudyEventOID, Ordinal, Start Date, End Date")
	@ApiResponses(value = {
	        @ApiResponse(code = 200, message = "Successful operation"),
	        @ApiResponse(code = 400, message = "Bad Request -- Normally means Found validation errors, for detail please see the error list: <br /> ")})
	@RequestMapping(value = "clinicaldata/studies/{studyOID}/sites/{siteOID}/events/bulk", method = RequestMethod.POST,consumes = {"multipart/form-data"})
    @Async
	public ResponseEntity<Object> scheduleBulkEventAtSiteLevel(HttpServletRequest request,
			MultipartFile file,
			@PathVariable("studyOID") String studyOID,
			@PathVariable("siteOID") String siteOID) throws Exception {
		
    	 CompletableFuture<ResponseEntity<Object>> future = CompletableFuture.supplyAsync(() -> {
    		 UserAccountBean ub = getUserAccount(request);    		
    		 return scheduleEvent(request,file, studyOID, siteOID,ub);
    	 });
    	 
    	  String uuid = UUID.randomUUID().toString();
          System.out.println(uuid);
          synchronized (expiringMap) {
              expiringMap.put(uuid, future);
          }
          ResponseEntity response = null;
          RestReponseDTO responseDTO = new RestReponseDTO();
    		String finalMsg = "The schedule job is running, here is the schedule job ID:" + uuid;
    		responseDTO.setMessage(finalMsg);
    		response = new ResponseEntity(responseDTO, org.springframework.http.HttpStatus.OK);
    		
    		return response;
    	
	}
    
    @ApiOperation(value = "To schedule an event for participants at study level in bulk",  notes = "Will read the information of SudyOID,ParticipantID, StudyEventOID, Ordinal, Start Date, End Date")
	@ApiResponses(value = {
	        @ApiResponse(code = 200, message = "Successful operation"),
	        @ApiResponse(code = 400, message = "Bad Request -- Normally means Found validation errors, for detail please see the error list: <br /> ")})
	@RequestMapping(value = "clinicaldata/studies/{studyOID}/events/bulk", method = RequestMethod.POST,consumes = {"multipart/form-data"})
    @Async
	public ResponseEntity<Object> scheduleBulkEventAtStudyLevel(HttpServletRequest request, 
			MultipartFile file,
			@PathVariable("studyOID") String studyOID) throws Exception {
		
    	 CompletableFuture<ResponseEntity<Object>> future = CompletableFuture.supplyAsync(() -> {
    		 UserAccountBean ub = getUserAccount(request);    		 
    		 return scheduleEvent(request,file, studyOID, null, ub);
    	 });
    	 
    	 String uuid = UUID.randomUUID().toString();
         
         synchronized (expiringMap) {
              expiringMap.put(uuid, future);
          }
        
        ResponseEntity response = null;
        RestReponseDTO responseDTO = new RestReponseDTO();
  		String finalMsg = "The schedule job is running, here is the schedule job ID:" + uuid;
  		responseDTO.setMessage(finalMsg);
  		response = new ResponseEntity(responseDTO, org.springframework.http.HttpStatus.OK);
  		
  		return response;
		
    	
	}
    


	private ResponseEntity<Object> scheduleEvent(HttpServletRequest request,MultipartFile file, String studyOID, String siteOID,UserAccountBean ub) {
			
		ResponseEntity response = null;
		String logFileName = null;
		
		if (!file.isEmpty()) {
			 String fileNm = file.getOriginalFilename();
			 
			 //only support CSV file
			 if(!(fileNm.endsWith(".csv")) ){
				 throw new OpenClinicaSystemException("errorCode.notSupportedFileFormat","The file format is not supported at this time, please send CSV file, like *.csv ");
			 }
	
			 int endIndex = fileNm.indexOf(".csv"); 
			 String originalFileNm =  fileNm.substring(0, endIndex);
			 logFileName = this.getRestfulServiceHelper().getMessageLogger().getLogfileNamewithTimeStamp(originalFileNm);
			
		}else {
			logger.info("errorCode.emptyFile -- The file is empty ");
			throw new OpenClinicaSystemException("errorCode.emptyFile","The file is empty ");
		}
		
		try {
			 
				// read csv file
				 ArrayList<StudyEventScheduleDTO> studyEventScheduleDTOList = RestfulServiceHelper.readStudyEventScheduleBulkCSVFile(file, studyOID, siteOID);
				 
				 //schedule events
				 for(StudyEventScheduleDTO studyEventScheduleDTO:studyEventScheduleDTOList) {
					 String studyEventOID = studyEventScheduleDTO.getStudyEventOID();
					 String participantId = studyEventScheduleDTO.getSubjectKey();
					 String sampleOrdinalStr = studyEventScheduleDTO.getOrdinal();
					 String startDate = studyEventScheduleDTO.getStartDate();
					 String endDate = studyEventScheduleDTO.getEndDate();
					 int rowNum = studyEventScheduleDTO.getRowNum();
					 
					 RestReponseDTO responseTempDTO = null;	    	    	
				     String status="";
				     String message="";
				     	
				     responseTempDTO = studyEventService.scheduleStudyEvent(ub, studyOID, siteOID, studyEventOID, participantId, sampleOrdinalStr, startDate, endDate);
				    
				     /**
			         *  response
			         */
			    	if(responseTempDTO.getErrors().size() > 0) {
			    		status = "Failed";
			    		message = responseTempDTO.getErrors().get(0).toString();
			 	      
			    	}else{
			    		status = "Sucessful";
			    		message = "Scheduled";
			 	       
			    	}
                    
			    	/**
			         * log error / info into log file 
			         * Response should be a logfile with columns - 
			         * Row, ParticipantID, StudyEventOID, Ordinal, Status, ErrorMessage
			         */ 
                	String recordNum = null;
                	String headerLine = "Row|ParticipantID|StudyEventOID|Ordinal|Status|ErrorMessage";
                	String msg = null;
                	msg = rowNum + "|" + participantId + "|" + studyEventOID +"|"+ sampleOrdinalStr +"|" + status + "|"+message;
                	String subDir = "study-event-schedule";
    	    		this.getRestfulServiceHelper().getMessageLogger().writeToLog(subDir,logFileName, headerLine, msg, ub);
    	    		
    	    		
				 }
				 
			
			
		} catch (Exception e) {
			logger.error("Error " + e.getMessage());
		}
		
		RestReponseDTO responseDTO = new RestReponseDTO();
		String finalMsg = "Please check detail schedule information in log file:" + logFileName;
		responseDTO.setMessage(finalMsg);
		response = new ResponseEntity(responseDTO, org.springframework.http.HttpStatus.OK);
		
		return response;
	}
	public ResponseEntity<Object> scheduleEvent(HttpServletRequest request, String studyOID, String siteOID,String studyEventOID,String participantId,String sampleOrdinalStr, String startDate,String endDate){
	    	ResponseEntity response = null;
	    	RestReponseDTO responseDTO = null;	    	    	
	    	String message="";
	    	
	    	
	    	responseDTO = studyEventService.scheduleStudyEvent(request, studyOID, siteOID, studyEventOID, participantId, sampleOrdinalStr, startDate, endDate);
	    	
	    	/**
	         *  response
	         */
	    	if(responseDTO.getErrors().size() > 0) {
	    		message = "Scheduled event " + studyEventOID + " for participant "+ participantId + " in study " + studyOID + " Failed.";
	 	        responseDTO.setMessage(message);

	 			response = new ResponseEntity(responseDTO, org.springframework.http.HttpStatus.BAD_REQUEST);
	    	}else{
	    		message = "Scheduled event " + studyEventOID + " for participant "+ participantId + " in study " + studyOID + " sucessfully.";
	 	        responseDTO.setMessage(message);

	 			response = new ResponseEntity(responseDTO, org.springframework.http.HttpStatus.OK);
	    	}
	       
	    	return response;
	      
	    
	    }
	
	@ApiOperation(value = "To check schedule job status with job ID",  notes = " the job ID is included in the response when you run bulk schedule task")
	@SuppressWarnings("unchecked")
    @RequestMapping(value = "/scheduleJobs/{uuid}", method = RequestMethod.GET)
    public ResponseEntity<Object>  checkScheduleStatus(@PathVariable("uuid") String scheduleUuid,
                                                           HttpServletRequest request) {

	    Future<ResponseEntity<Object>> future = null;
        synchronized (expiringMap) {
            future = expiringMap.get(scheduleUuid);
        }
        if (future == null) {
            logger.info("Schedule Future :" + scheduleUuid + " couldn't be found");
            return new ResponseEntity<>("Schedule Future :" + scheduleUuid + " couldn't be found", HttpStatus.BAD_REQUEST);
        } else if (future.isDone()) {
            try {
                ResponseEntity<Object> objectResponseEntity = future.get();
                return new ResponseEntity<>("Completed", HttpStatus.OK);
            } catch (InterruptedException e) {
            	logger.error("Error " + e.getMessage());              
                return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
            } catch (CustomRuntimeException e) {
                
                return new ResponseEntity<>(e.getErrList(), HttpStatus.BAD_REQUEST);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause != null && cause instanceof CustomRuntimeException) {
                
                    return new ResponseEntity<>(((CustomRuntimeException) cause).getErrList(), HttpStatus.BAD_REQUEST);
                } else {
                    List<ErrorObj> err = new ArrayList<>();
                    ErrorObj errorObj = new ErrorObj(e.getMessage(), e.getMessage());
                    err.add(errorObj);
                    logger.error("Error " + e.getMessage());
                    
                    return new ResponseEntity<>(err, HttpStatus.BAD_REQUEST);
                }
            }
        } else {
            return new ResponseEntity<>(HttpStatus.PROCESSING.getReasonPhrase(), HttpStatus.OK);
        }
    
	}
  
	
	 /**
     * Helper Method to get the user account
     * 
     * @return UserAccountBean
     */
    public UserAccountBean getUserAccount(HttpServletRequest request) {
    	UserAccountBean userBean;    
    	
    	if(request.getSession().getAttribute("userBean") != null) {
    		userBean = (UserAccountBean) request.getSession().getAttribute("userBean");
    		
    	}else {
    		 Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    	        String username = null;
    	        if (principal instanceof UserDetails) {
    	            username = ((UserDetails) principal).getUsername();
    	        } else {
    	            username = principal.toString();
    	        }

			String schema = CoreResources.getRequestSchema();
			CoreResources.setRequestSchema("public");
    	        UserAccountDAO userAccountDAO = new UserAccountDAO(dataSource);
    	        userBean = (UserAccountBean) userAccountDAO.findByUserName(username);
			CoreResources.setRequestSchema(schema);

    	}
    	
    	return userBean;
       
	}
}


