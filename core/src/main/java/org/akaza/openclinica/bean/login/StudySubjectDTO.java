package org.akaza.openclinica.bean.login;

import java.util.ArrayList;
import java.util.Date;

import org.akaza.openclinica.bean.managestudy.StudyBean;

/**
 * 
 * @author Tao Li
 *
 */
public class StudySubjectDTO {

	private String personId;
    private String studySubjectId;
    private Date dateOfBirth;
    private String yearOfBirth;
    private char gender;
    private String studyOid;
    private String studyUniqueIdentifier;
    private Date dateReceived;
    private Date enrollmentDate;
    private String secondaryId;
    private String siteIdentifier;
    UserAccountBean owner;	    
	private ArrayList<ErrorObject> errors;
    private String message;
    private ArrayList<UserRole> assignUserRoles;
	private StudyBean study;
	
    public StudyBean getStudy() {
		return study;
	}
	public void setStudy(StudyBean study) {
		this.study = study;
	}
	public String getPersonId() {
		return personId;
	}
	public void setPersonId(String personId) {
		this.personId = personId;
	}
	public String getStudySubjectId() {
		return studySubjectId;
	}
	public void setStudySubjectId(String studySubjectId) {
		this.studySubjectId = studySubjectId;
	}
	public Date getDateOfBirth() {
		return dateOfBirth;
	}
	public void setDateOfBirth(Date dateOfBirth) {
		this.dateOfBirth = dateOfBirth;
	}
	public String getYearOfBirth() {
		return yearOfBirth;
	}
	public void setYearOfBirth(String yearOfBirth) {
		this.yearOfBirth = yearOfBirth;
	}
	public char getGender() {
		return gender;
	}
	public void setGender(char gender) {
		this.gender = gender;
	}
	public String getStudyOid() {
		return studyOid;
	}
	public void setStudyOid(String studyOid) {
		this.studyOid = studyOid;
	}
	public String getStudyUniqueIdentifier() {
		return studyUniqueIdentifier;
	}
	public void setStudyUniqueIdentifier(String studyUniqueIdentifier) {
		this.studyUniqueIdentifier = studyUniqueIdentifier;
	}
	public Date getDateReceived() {
		return dateReceived;
	}
	public void setDateReceived(Date dateReceived) {
		this.dateReceived = dateReceived;
	}
	public Date getEnrollmentDate() {
		return enrollmentDate;
	}
	public void setEnrollmentDate(Date enrollmentDate) {
		this.enrollmentDate = enrollmentDate;
	}
	public String getSecondaryId() {
		return secondaryId;
	}
	public void setSecondaryId(String secondaryId) {
		this.secondaryId = secondaryId;
	}
	public String getSiteIdentifier() {
		return siteIdentifier;
	}
	public void setSiteIdentifier(String siteIdentifier) {
		this.siteIdentifier = siteIdentifier;
	}
	public UserAccountBean getOwner() {
		return owner;
	}
	public void setOwner(UserAccountBean owner) {
		this.owner = owner;
	}
	public ArrayList<ErrorObject> getErrors() {
		return errors;
	}
	public void setErrors(ArrayList<ErrorObject> errors) {
		this.errors = errors;
	}
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
	public ArrayList<UserRole> getAssignUserRoles() {
		return assignUserRoles;
	}
	public void setAssignUserRoles(ArrayList<UserRole> assignUserRoles) {
		this.assignUserRoles = assignUserRoles;
	}
	
}
