/**
 * Student.java
 *
 * Represents a registered student of the Online Library System.
 *
 * OOP concepts demonstrated:
 *   - Encapsulation: all fields are private and exposed only through getters/setters.
 *   - Single responsibility: this class only models student identity data.
 */
public class Student {

    private String studentId;     // Unique student ID number (e.g. 2023-00123)
    private String fullName;      // Full legal name
    private String phone;         // Contact number
    private boolean agreedTerms;  // Whether the student accepted the Terms and Conditions
    private String registeredAt;  // ISO-8601 timestamp of registration

    public Student() {
    }

    public Student(String studentId, String fullName, String phone,
                   boolean agreedTerms, String registeredAt) {
        setStudentId(studentId);
        setFullName(fullName);
        setPhone(phone);
        setAgreedTerms(agreedTerms);
        setRegisteredAt(registeredAt);
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = Validation.requireStudentId(studentId);
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = Validation.requireFullName(fullName);
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = Validation.requirePhone(phone);
    }

    public boolean isAgreedTerms() {
        return agreedTerms;
    }

    public void setAgreedTerms(boolean agreedTerms) {
        if (!agreedTerms) {
            throw new IllegalArgumentException("A registered student must agree to the Terms and Conditions.");
        }
        this.agreedTerms = agreedTerms;
    }

    public String getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(String registeredAt) {
        this.registeredAt = Validation.requireTimestamp(registeredAt, "Registration timestamp");
    }
}
