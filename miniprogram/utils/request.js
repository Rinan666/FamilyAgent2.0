const { BASE_URL } = require("./config");

function getAppSession() {
  const app = getApp();
  return app && app.getSession ? app.getSession() : null;
}

function redirectToLogin() {
  wx.reLaunch({
    url: "/pages/login/index",
  });
}

function request(options) {
  const session = getAppSession();
  const token = session && session.token ? session.token : "";

  return new Promise((resolve, reject) => {
    wx.request({
      url: `${BASE_URL}${options.url}`,
      method: options.method || "GET",
      data: options.data || undefined,
      header: {
        "Content-Type": "application/json",
        ...(token ? { Authorization: token } : {}),
      },
      success(response) {
        const payload = response.data;
        if (response.statusCode === 401 || (payload && payload.code === 401)) {
          const app = getApp();
          if (app && app.clearSession) {
            app.clearSession();
          }
          redirectToLogin();
          reject(new Error("登录状态已失效，请重新登录"));
          return;
        }

        if (response.statusCode >= 500) {
          reject(new Error("服务暂时不可用，请稍后重试"));
          return;
        }

        if (!payload || typeof payload !== "object") {
          reject(new Error("服务返回了无效响应"));
          return;
        }

        if (payload.code !== 200) {
          reject(new Error(payload.message || "请求失败"));
          return;
        }

        resolve(payload.data);
      },
      fail() {
        reject(new Error("网络请求失败，请检查网络或服务地址"));
      },
    });
  });
}

module.exports = {
  request,
};
