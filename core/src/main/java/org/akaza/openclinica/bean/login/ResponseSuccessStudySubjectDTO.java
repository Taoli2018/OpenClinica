package org.akaza.openclinica.bean.login;

/**
 * 
 * @author Tao Li
 *
 */
public class ResponseSuccessStudySubjectDTO {
	
	private String message;
	private String studySubjectUniqueIdentifier;
	private String studySubjectId;		
	private String studyUniqueIdentifier;
	
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
	public String getStudySubjectUniqueIdentifier() {
		return studySubjectUniqueIdentifier;
	}
	public void setStudySubjectUniqueIdentifier(String studySubjectUniqueIdentifier) {
		this.studySubjectUniqueIdentifier = studySubjectUniqueIdentifier;
	}
	public String getStudySubjectId() {
		return studySubjectId;
	}
	public void setStudySubjectId(String studySubjectId) {
		this.studySubjectId = studySubjectId;
	}
	
	public String getStudyUniqueIdentifier() {
		return studyUniqueIdentifier;
	}
	public void setStudyUniqueIdentifier(String studyUniqueIdentifier) {
		this.studyUniqueIdentifier = studyUniqueIdentifier;
	}

}
