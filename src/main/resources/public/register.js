(() => {
    const loginSection = document.getElementById("login-section");
    const registerSection = document.getElementById("register-section");
    const registerForm = document.getElementById("register-form");

    const registerError = document.getElementById("register-error");
    const loginMessage = document.getElementById("login-message");

    function showLogin() {
        registerSection.hidden = true;
        loginSection.hidden = false;
        document.getElementById("username").focus();
    }

    document.getElementById("show-register")
        .addEventListener("click", () => {
            loginSection.hidden = true;
            registerSection.hidden = false;

            registerError.textContent = "";
            loginMessage.textContent = "";

            document.getElementById("register-username").focus();
        });

    document.getElementById("show-login").addEventListener("click", showLogin);

    registerForm.addEventListener("submit", async event => {
        event.preventDefault();
        registerError.textContent = "";

        const username = document.getElementById("register-username").value.trim();

        const password = document.getElementById("register-password").value;

        const confirmPassword = document.getElementById("confirm-password").value;

        if (!username) {
            registerError.textContent = "Please enter a username.";
            return;
        }

        if (password !== confirmPassword) {
            registerError.textContent = "Passwords do not match.";
            return;
        }

        const button = registerForm.querySelector('button[type="submit"]');
        button.disabled = true;

        try {
            const response = await fetch("/api/auth/register", {
                method: "POST", headers: {"Content-Type": "application/json"},
                body: JSON.stringify({ username, password })
            });

            if (!response.ok) {
                let message = "Could not create account.";

                const body = await response.text();

                try {
                    const error = JSON.parse(body);

                    if (typeof error.error === "string") {
                        message = error.error;
                    } else if (typeof error.message === "string") {
                        message = error.message;
                    }
                } catch {
                    if (response.status === 409) {
                        message = "That username is already taken.";
                    }
                }

                registerError.textContent = message;
                return;
            }

            registerForm.reset();

            document.getElementById("username").value = username;
            document.getElementById("password").value = "";
            document.getElementById("login-error").textContent = "";

            loginMessage.textContent = "Account created. You can now log in.";

            showLogin();

        } catch (error) {
            registerError.textContent = "Could not reach the server.";
            console.error("Registration failed:", error);

        } finally {
            button.disabled = false;
        }
    });
})();