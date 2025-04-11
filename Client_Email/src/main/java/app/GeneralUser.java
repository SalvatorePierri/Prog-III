package app;

public class GeneralUser {
    private String email;

    public enum role{
        Admin,
        User
    }

    private role role;

    public GeneralUser(String email, role role){
        this.email = email;
        this.role = role;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public role getRole() {
        return role;
    }

    public void setRole(role role) {
        this.role = role;
    }
}

