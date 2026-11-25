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
        this.studentId = studentId;
        this.fullName = fullName;
        this.phone = phone;
        this.agreedTerms = agreedTerms;
        this.registeredAt = registeredAt;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public boolean isAgreedTerms() {
        return agreedTerms;
    }

    public void setAgreedTerms(boolean agreedTerms) {
        this.agreedTerms = agreedTerms;
    }

    public String getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(String registeredAt) {
        this.registeredAt = registeredAt;
    }
}
