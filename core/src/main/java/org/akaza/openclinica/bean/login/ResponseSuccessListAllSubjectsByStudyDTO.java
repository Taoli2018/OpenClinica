package org.akaza.openclinica.bean.login;

import java.util.List;

import org.akaza.openclinica.domain.datamap.StudySubject;

public class ResponseSuccessListAllSubjectsByStudyDTO {

	private String studyOid;
	private String siteOid;
	private String message;
	protected List<StudySubjectDTO> studySubjects;
	
	public String getStudyOid() {
		return studyOid;
	}
	public void setStudyOid(String studyOid) {
		this.studyOid = studyOid;
	}
	public String getSiteOid() {
		return siteOid;
	}
	public void setSiteOid(String siteOid) {
		this.siteOid = siteOid;
	}
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
	public List<StudySubjectDTO> getStudySubjects() {
		return studySubjects;
	}
	public void setStudySubjects(List<StudySubjectDTO> studySubjects) {
		this.studySubjects = studySubjects;
	}
	
	
	  
	

}
