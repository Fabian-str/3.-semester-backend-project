const loginForm = document.getElementById("login-form");

if (loginForm) {

    loginForm.addEventListener("submit", async (event) => {

        event.preventDefault();

        const username = document.getElementById("username").value;

        const password = document.getElementById("password").value;

        const response = await fetch("/api/auth/login", {
            method: "POST",
            headers: {"Content-Type": "application/json"}, body: JSON.stringify({username, password})
        });

        if (!response.ok) {

            document.getElementById("login-error").textContent = "Invalid username or password";

            return;
        }

        const data = await response.json();

        localStorage.setItem("token", data.token);

        localStorage.setItem("username", data.username);

        window.location.href = "/dashboard.html";});
}