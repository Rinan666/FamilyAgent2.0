const ENVIRONMENTS = {
  local: "http://127.0.0.1:8080/api",
  prod: "https://your-api-domain.example.com/api",
};

const ACTIVE_ENV = "local";

module.exports = {
  BASE_URL: ENVIRONMENTS[ACTIVE_ENV],
  ENVIRONMENTS,
};
