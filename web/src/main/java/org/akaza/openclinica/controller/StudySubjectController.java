package org.akaza.openclinica.controller;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ResourceBundle;

import javax.servlet.http.HttpServletRequest;

import org.akaza.openclinica.bean.core.Role;
import org.akaza.openclinica.bean.core.Status;
import org.akaza.openclinica.bean.login.ErrorObject;
import org.akaza.openclinica.bean.login.ResponseDTO;
import org.akaza.openclinica.bean.login.ResponseSuccessListAllSubjectsByStudyDTO;
import org.akaza.openclinica.bean.login.ResponseSuccessStudySubjectDTO;
import org.akaza.openclinica.bean.login.StudySubjectDTO;
import org.akaza.openclinica.bean.login.StudyUserRoleBean;
import org.akaza.openclinica.bean.login.UserAccountBean;
import org.akaza.openclinica.bean.login.UserRole;
import org.akaza.openclinica.bean.managestudy.StudyBean;
import org.akaza.openclinica.bean.managestudy.SubjectTransferBean;
import org.akaza.openclinica.bean.submit.SubjectBean;
import org.akaza.openclinica.dao.hibernate.StudySubjectDao;
import org.akaza.openclinica.dao.login.UserAccountDAO;
import org.akaza.openclinica.dao.managestudy.StudyDAO;
import org.akaza.openclinica.domain.datamap.StudySubject;
import org.akaza.openclinica.exception.OpenClinicaSystemException;
import org.akaza.openclinica.service.subject.SubjectService;
import org.akaza.openclinica.validator.SubjectTransferValidator;
import org.apache.commons.dbcp.BasicDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.validation.DataBinder;
import org.springframework.validation.Errors;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import net.sf.json.JSONObject;

/**
 * 
 * @author Tao Li
 *
 */
@Controller
@RequestMapping(value = "/auth/api/v1/studysubject")
public class StudySubjectController {

	@Autowired
	@Qualifier("dataSource")
	private BasicDataSource dataSource;
	
	@Autowired
	private UserAccountDAO udao;
	
	@Autowired
	private StudySubjectDao ssDao;
	
	@Autowired
	private SubjectService subjectService;
   
	StudyDAO studyDao;
	UserAccountDAO userAccountDao;
	
	private String dateFormat;	 
	protected final Logger logger = LoggerFactory.getLogger(getClass().getName());
	
	/**
	 * 
	 * @param request
	 * @param map
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/", method = RequestMethod.POST)
	public ResponseEntity<Object> createNewStudySubject(HttpServletRequest request, @RequestBody HashMap<String, Object> map) throws Exception {
		
		ArrayList<ErrorObject> errorObjects = new ArrayList<ErrorObject>();
		ErrorObject errorOBject = null;
		ResponseEntity<Object> response = null;
		String validation_failed_message = "VALIDATION FAILED";
		String validation_passed_message = "SUCCESS";
	   
	    
	    ArrayList<UserRole> assignUserRoles = (ArrayList<UserRole>) map.get("assignUserRoles");

		ArrayList<UserRole> userList = new ArrayList<>();
		
		if (assignUserRoles != null) {
			for (Object userRole : assignUserRoles) {
				UserRole uRole = new UserRole();
				uRole.setUsername((String) ((HashMap<String, Object>) userRole).get("username"));
				uRole.setRole((String) ((HashMap<String, Object>) userRole).get("role"));
				udao = new UserAccountDAO(dataSource);
				UserAccountBean assignedUserBean = (UserAccountBean) udao.findByUserName(uRole.getUsername());
				if (assignedUserBean == null || !assignedUserBean.isActive()) {
					errorOBject = createErrorObject("Study Object", "The Assigned Username " + uRole.getUsername() + " is not a Valid User", "Assigned User");
					errorObjects.add(errorOBject);
				}

				ResourceBundle resterm = org.akaza.openclinica.i18n.util.ResourceBundleProvider.getTermsBundle();

				if (getStudyRole(uRole.getRole(), resterm) == null) {
					errorOBject = createErrorObject("Study Object", "Assigned Role for " + uRole.getUsername() + " is not a Valid Study Role", "Assigned Role");
					errorObjects.add(errorOBject);
				}
				userList.add(uRole);
			}
		}
		
		
		SubjectTransferBean subjectTransferBean = this.transferToSubject(map);
		StudySubjectDTO studySubjectDTO = this.buildStudySubjectDTO(map);
		
		if(this.subjectService.getSubjectDao().findByUniqueIdentifier(subjectTransferBean.getPersonId()).getId() != 0) {
			errorOBject = createErrorObject("Study Object", "person ID  " + subjectTransferBean.getPersonId() + " already exists in this system, please use different ID", "Create Study Subject");
			errorObjects.add(errorOBject);
		}
		
		subjectTransferBean.setOwner(getUserAccount(request));
        SubjectTransferValidator subjectTransferValidator = new SubjectTransferValidator(dataSource);
        Errors errors = null;
        
        DataBinder dataBinder = new DataBinder((subjectTransferBean));
        errors = dataBinder.getBindingResult();
        subjectTransferValidator.validate((subjectTransferBean), errors);
        
        if(errors.hasErrors()) {
        	ArrayList validerrors = new ArrayList(errors.getAllErrors());
        	Iterator errorIt = validerrors.iterator();
        	while(errorIt.hasNext()) {
        		ObjectError oe = (ObjectError) errorIt.next();
        		
        		errorOBject = createErrorObject("Subject Object Field", oe.getDefaultMessage(),oe.getCode());
				errorObjects.add(errorOBject);
        		
        	}
        }
        
        if (errorObjects != null && errorObjects.size() != 0) {
        	studySubjectDTO.setErrors(errorObjects);
    		studySubjectDTO.setMessage(validation_failed_message);
    		response = new ResponseEntity(studySubjectDTO, org.springframework.http.HttpStatus.BAD_REQUEST);
        } else {        				
		  	String label = create(subjectTransferBean);
            studySubjectDTO.setMessage(validation_passed_message);
            
            ResponseSuccessStudySubjectDTO responseSuccess = new ResponseSuccessStudySubjectDTO();
            responseSuccess.setMessage(studySubjectDTO.getMessage());
            responseSuccess.setStudySubjectId(studySubjectDTO.getStudySubjectId());
            responseSuccess.setStudySubjectUniqueIdentifier(studySubjectDTO.getPersonId());
            responseSuccess.setStudyUniqueIdentifier(studySubjectDTO.getStudyUniqueIdentifier());

			response = new ResponseEntity(responseSuccess, org.springframework.http.HttpStatus.OK);
        }
        
		return response;
		
	}
	
	
	
	@RequestMapping(value = "{study}", method = RequestMethod.GET)
	public ResponseEntity<Object> listStudySubjectsInStudy(@PathVariable("study") String studyOid,HttpServletRequest request) throws Exception {
	
		return listStudySubjects(studyOid, null, request);
	}

	
	@RequestMapping(value = "{study}/{site}", method = RequestMethod.GET)
	public ResponseEntity<Object> listStudySubjectsInStudySite(@PathVariable("study") String studyOid,@PathVariable("site") String siteOid,HttpServletRequest request) throws Exception {
	
		return listStudySubjects(studyOid, siteOid, request);
	}


	/**
	 * @param studyOid
	 * @param siteOid
	 * @param request
	 * @return
	 * @throws Exception
	 */
	private ResponseEntity<Object> listStudySubjects(String studyOid, String siteOid, HttpServletRequest request)
			throws Exception {
		ArrayList<ErrorObject> errorObjects = new ArrayList<ErrorObject>();
		ErrorObject errorOBject = null;
		ResponseEntity<Object> response = null;
		String validation_failed_message = "VALIDATION FAILED";
		String validation_passed_message = "SUCCESS";		
	
		String studyIdentifier = studyOid;	
		String siteIdentifier = siteOid;
		
		 try {
	         	     
	            StudyBean studyBean = null;
	            try {
	            	studyBean = validateRequestAndReturnStudy(studyIdentifier, siteIdentifier,request);
	            } catch (OpenClinicaSystemException e) {	                	               	                
	                errorOBject = createErrorObject("List Study Object failed", "studyRef:  " + studyIdentifier + " siteRef: " + siteIdentifier, e.getErrorCode());
	    			errorObjects.add(errorOBject);
	    			ResponseDTO responseDTO = new  ResponseDTO();
	    			responseDTO.setErrors(errorObjects);
	    			responseDTO.setMessage(e.getMessage());
	        		response = new ResponseEntity(responseDTO, org.springframework.http.HttpStatus.EXPECTATION_FAILED);
	            }
	            
	            if(studyBean != null) {
	            	ResponseSuccessListAllSubjectsByStudyDTO responseSuccess =  new ResponseSuccessListAllSubjectsByStudyDTO();
	            	
	            	ArrayList<StudySubjectDTO> studySubjectDTOs = getStudySubjectDTOs(studyIdentifier, siteIdentifier,studyBean);
	            	  
	 	            responseSuccess.setMessage(validation_passed_message +  " - Found Study Subjects: " + studySubjectDTOs.size() );
	            	responseSuccess.setStudyOid(studyIdentifier);
	            	responseSuccess.setSiteOid(siteIdentifier);
	 	            responseSuccess.setStudySubjects(studySubjectDTOs);
	 	          
	            	
	 	            response = new ResponseEntity(responseSuccess, org.springframework.http.HttpStatus.OK);
	            }	           
	           
	        } catch (Exception eee) {
	            eee.printStackTrace();
	            throw eee;
	        }
		 
		return response;
	}
	
	/**
	 * 
	 * @param studyIdentifier
	 * @param siteIdentifier
	 * @param study
	 * @return
	 * @throws Exception
	 */
	 private ArrayList<StudySubjectDTO> getStudySubjectDTOs(String studyIdentifier, String siteIdentifier,StudyBean study) throws Exception {
		 	      
	        List<StudySubject> studySubjects = this.ssDao.findAllByStudy(study.getId());
	        
	        ArrayList studySubjectDTOs = new ArrayList<StudySubjectDTO>(); 
	        
	        for(StudySubject studySubject:studySubjects) {
	        	StudySubjectDTO ssDTO= new StudySubjectDTO();
	        	ssDTO.setMessage("Success");	        	
	        	ssDTO.setDateOfBirth(studySubject.getSubject().getDateOfBirth());	      
	        	ssDTO.setYearOfBirth(getYear(ssDTO.getDateOfBirth()) + "");
	        	ssDTO.setEnrollmentDate(studySubject.getEnrollmentDate());
	        	ssDTO.setGender(studySubject.getSubject().getGender());
	        	ssDTO.setStudySubjectId(studySubject.getLabel());
	        	ssDTO.setSecondaryId(studySubject.getSecondaryLabel());	        	
	        	ssDTO.setPersonId(studySubject.getSubject().getUniqueIdentifier());
	        	ssDTO.setDateReceived(studySubject.getDateCreated());
	        	
	        	if(studyIdentifier!=null && siteIdentifier!=null) {
	        		ssDTO.setSiteIdentifier(siteIdentifier);
		        	ssDTO.setStudyUniqueIdentifier(studyIdentifier);
	        	}else {
	        		ssDTO.setStudyOid(studySubject.getStudy().getOc_oid());
		        	ssDTO.setStudyUniqueIdentifier(studySubject.getStudy().getUniqueIdentifier());
	        	}
	        	
	        	
	        	studySubjectDTOs.add(ssDTO);
	        }
	        
	        return studySubjectDTOs;
	    }
	 
	/**
     * Validate the listStudySubjectsInStudy request.
     * 
     * @param studyRef
     * @return StudyBean
     */
    private StudyBean validateRequestAndReturnStudy(String studyIdentifier, String siteIdentifier,HttpServletRequest request) {

       
        if (studyIdentifier == null && siteIdentifier == null) {
            throw new OpenClinicaSystemException("studySubjectEndpoint.provide_valid_study_site", "Provide a valid study/site.");
        }
        if (studyIdentifier != null && siteIdentifier == null) {
            StudyBean study = getStudyDao().findByUniqueIdentifier(studyIdentifier);
            if (study == null) {
                throw new OpenClinicaSystemException("studySubjectEndpoint.invalid_study_identifier", "The study identifier you provided is not valid.");
            }
            StudyUserRoleBean studySur = getUserAccountDao().findRoleByUserNameAndStudyId(getUserAccount(request).getName(), study.getId());
            if (studySur.getStatus() != Status.AVAILABLE) {
                throw new OpenClinicaSystemException("studySubjectEndpoint.insufficient_permissions",
                        "You do not have sufficient privileges to proceed with this operation.");
            }
            return study;
        }
        if (studyIdentifier != null && siteIdentifier != null) {
            StudyBean study = getStudyDao().findByUniqueIdentifier(studyIdentifier);
            StudyBean site = getStudyDao().findByUniqueIdentifier(siteIdentifier);
            if (study == null || site == null || site.getParentStudyId() != study.getId()) {
                throw new OpenClinicaSystemException("studySubjectEndpoint.invalid_study_site_identifier",
                        "The study/site identifier you provided is not valid.");
            }
            StudyUserRoleBean siteSur = getUserAccountDao().findRoleByUserNameAndStudyId(getUserAccount(request).getName(), site.getId());
            if (siteSur.getStatus() != Status.AVAILABLE) {
                throw new OpenClinicaSystemException("studySubjectEndpoint.insufficient_permissions",
                        "You do not have sufficient privileges to proceed with this operation.");
            }
            return site;
        }
        return null;
    }
    
	/**
     * Create the Subject object if it is not already in the system.
     * 
     * @param subjectTransferBean
     * @return String
     */
    private String create(SubjectTransferBean subjectTransferBean) {
          logger.debug("creating subject transfer");
          return createSubject(subjectTransferBean);    
    }
    
    /**
     * 
     * @param subjectTransfer
     * @return
     */
	 private String createSubject(SubjectTransferBean subjectTransfer) {
	        SubjectBean subject = new SubjectBean();
	        subject.setUniqueIdentifier(subjectTransfer.getPersonId());
	        subject.setLabel(subjectTransfer.getStudySubjectId());
	        subject.setDateOfBirth(subjectTransfer.getDateOfBirth());
	        // below added tbh 04/2011
	        if (subject.getDateOfBirth() != null) {
	        	subject.setDobCollected(true);
	        } else {
	        	subject.setDobCollected(false);
	        }
	        // >> above added tbh 04/2011, mantis issue having to 
	        // deal with not being able to change DOB after a submit
	        subject.setGender(subjectTransfer.getGender());
	        if (subjectTransfer.getOwner() != null) {
	            subject.setOwner(subjectTransfer.getOwner());
	        }
	        subject.setCreatedDate(new Date());
	        return this.subjectService.createSubject(subject, subjectTransfer.getStudy(), subjectTransfer.getEnrollmentDate(), subjectTransfer.getSecondaryId());
	    }

	 /**
     * Helper Method to get the user account
     * 
     * @return UserAccountBean
     */
    private UserAccountBean getUserAccount(HttpServletRequest request) {
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
    	        UserAccountDAO userAccountDao = new UserAccountDAO(dataSource);
    	        userBean = (UserAccountBean) userAccountDao.findByUserName(username);
    	}
    	
    	return userBean;
       
    }
	
    /**
     * 
     * @param resource
     * @param code
     * @param field
     * @return
     */
	public ErrorObject createErrorObject(String resource, String code, String field) {
		ErrorObject errorOBject = new ErrorObject();
		errorOBject.setResource(resource);
		errorOBject.setCode(code);
		errorOBject.setField(field);
		return errorOBject;
	}
	
	/**
	 * 
	 * @param roleName
	 * @param resterm
	 * @return
	 */
	public Role getStudyRole(String roleName, ResourceBundle resterm) {
		if (roleName.equalsIgnoreCase(resterm.getString("Study_Director").trim())) {
			return Role.STUDYDIRECTOR;
		} else if (roleName.equalsIgnoreCase(resterm.getString("Study_Coordinator").trim())) {
			return Role.COORDINATOR;
		} else if (roleName.equalsIgnoreCase(resterm.getString("Investigator").trim())) {
			return Role.INVESTIGATOR;
		} else if (roleName.equalsIgnoreCase(resterm.getString("Data_Entry_Person").trim())) {
			return Role.RESEARCHASSISTANT;
		} else if (roleName.equalsIgnoreCase(resterm.getString("Monitor").trim())) {
			return Role.MONITOR;
		} else
			return null;
	}

	/**
	 * 
	 * @param map
	 * @return
	 * @throws ParseException
	 * @throws Exception
	 */
	 private SubjectTransferBean transferToSubject(HashMap<String, Object> map) throws ParseException, Exception {
	 	   			
		    String studySubjectId = (String) map.get("label");
		    String secondaryIdValue = (String) map.get("secondaryLabel");
		    String enrollmentDateValue = (String) map.get("enrollmentDate");
							
			JSONObject subject = JSONObject.fromObject(map.get("subject"));
		    String personId = (String) subject.get("uniqueIdentifier");		    
		    String dateOfBirthValue = (String) subject.get("dateOfBirth");
		    String yearOfBirth = (String) subject.get("yearOfBirth");
		    String gender = (String) subject.get("gender");
		    
		    JSONObject study = JSONObject.fromObject(map.get("studyRef"));
		    String studyIdentifier = (String) study.get("identifier");
		    
		    JSONObject site = study.getJSONObject("siteRef");
		    String siteIdentifier = null;
		    if(site != null && site.get("identifier") != null) {
		    	siteIdentifier = (String) site.get("identifier");	
		    }  
		    
		    SubjectTransferBean subjectTransferBean = new SubjectTransferBean();

	        subjectTransferBean.setStudyOid(studyIdentifier);
	        subjectTransferBean.setSiteIdentifier(siteIdentifier);
	        subjectTransferBean.setPersonId(personId);
	        subjectTransferBean.setStudySubjectId(studySubjectId);	        
	        subjectTransferBean.setGender(gender.charAt(0));
	       
	        subjectTransferBean.setDateOfBirth((dateOfBirthValue == null || dateOfBirthValue.length()==0)? null : getDate(dateOfBirthValue));
	        subjectTransferBean.setSecondaryId(secondaryIdValue == null ? "" : secondaryIdValue);
	        subjectTransferBean.setYearOfBirth(yearOfBirth);
	        subjectTransferBean.setEnrollmentDate(getDate(enrollmentDateValue));

	      
	        return subjectTransferBean;
		 
	 }
	 
	 /**
	  * 
	  * @param map
	  * @return
	  * @throws ParseException
	  * @throws Exception
	  */
	 private StudySubjectDTO buildStudySubjectDTO(HashMap<String, Object> map) throws ParseException, Exception {
	 				
		    String studySubjectId = (String) map.get("label");
		    String secondaryIdValue = (String) map.get("secondaryLabel");
		    String enrollmentDateValue = (String) map.get("enrollmentDate");
							
		    JSONObject subject = JSONObject.fromObject(map.get("subject"));
		    String personId = (String) subject.get("uniqueIdentifier");		    
		    String dateOfBirthValue = (String) subject.get("dateOfBirth");
		    String yearOfBirth = (String) subject.get("yearOfBirth");
		    String gender = (String) subject.get("gender");
		    
		    JSONObject study = JSONObject.fromObject(map.get("studyRef"));
		    String studyIdentifier = (String) study.get("identifier");
		    
		    JSONObject site = study.getJSONObject("siteRef");
		    String siteIdentifier = null;
		    if(site != null && site.get("identifier") != null) {
		    	siteIdentifier = (String) site.get("identifier");	
		    }  
		    
		    StudySubjectDTO studySubjectDTO = new StudySubjectDTO();

		    studySubjectDTO.setStudyUniqueIdentifier(studyIdentifier);
		    studySubjectDTO.setSiteIdentifier(siteIdentifier);
		    studySubjectDTO.setPersonId(personId);
		    studySubjectDTO.setStudySubjectId(studySubjectId);	        
		    studySubjectDTO.setGender(gender.charAt(0));
	       
		    studySubjectDTO.setDateOfBirth((dateOfBirthValue == null || dateOfBirthValue.length()==0)? null : getDate(dateOfBirthValue));
		    studySubjectDTO.setSecondaryId(secondaryIdValue == null ? "" : secondaryIdValue);
		    studySubjectDTO.setYearOfBirth(yearOfBirth);
		    studySubjectDTO.setEnrollmentDate(getDate(enrollmentDateValue));

	      
	        return studySubjectDTO;
		 
	 }
	 
	 
	 /**
     * Helper Method to resolve a date provided as a string to a Date object.
     * 
     * @param dateAsString
     * @return Date
     * @throws ParseException
     */
    private Date getDate(String dateAsString) throws ParseException, Exception {
        SimpleDateFormat sdf = new SimpleDateFormat(getDateFormat());
        sdf.setLenient(false);
        Date dd = sdf.parse(dateAsString);
        Calendar c = Calendar.getInstance();
        c.setTime(dd);
        if (c.get(Calendar.YEAR) < 1900 || c.get(Calendar.YEAR) > 9999) {
        	throw new Exception("Unparsable date: "+dateAsString);
        }
        return dd;
    }

    private int getYear(Date dt) throws ParseException, Exception {       
        Calendar c = Calendar.getInstance();
        c.setTime(dt);
        
        return c.get(Calendar.YEAR);
    }
    /**
     * 
     * @return
     */
    public String getDateFormat() {
		if(dateFormat == null) {
			dateFormat = "yyyy-MM-dd";
		}
		return dateFormat;
	}

    /**
     * 
     * @param dateFormat
     */
	public void setDateFormat(String dateFormat) {
		this.dateFormat = dateFormat;
	}
   
	/**
	 * 
	 * @return
	 */
	 public StudyDAO getStudyDao() {
        studyDao = studyDao != null ? studyDao : new StudyDAO(dataSource);
        return studyDao;
    }
	 
	 public UserAccountDAO getUserAccountDao() {
	        userAccountDao = userAccountDao != null ? userAccountDao : new UserAccountDAO(dataSource);
	        return userAccountDao;
	    }
}
