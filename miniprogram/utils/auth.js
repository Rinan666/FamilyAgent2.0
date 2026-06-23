const { request } = require("./request");

function loginWithWeChat(profile) {
  const nickname = profile && profile.nickname ? profile.nickname : "";
  const avatarUrl = profile && profile.avatarUrl ? profile.avatarUrl : "";

  return new Promise((resolve, reject) => {
    wx.login({
      success(loginRes) {
        if (!loginRes.code) {
          reject(new Error("微信登录失败，请重试"));
          return;
        }

        request({
          url: "/users/wechat/login",
          method: "POST",
          data: {
            code: loginRes.code,
            nickname,
            avatarUrl,
          },
        }).then((session) => {
          const app = getApp();
          app.setSession(session);
          resolve(session);
        }).catch(reject);
      },
      fail() {
        reject(new Error("无法获取微信登录凭证"));
      },
    });
  });
}

function logout() {
  const app = getApp();
  if (app && app.clearSession) {
    app.clearSession();
  }
}

module.exports = {
  loginWithWeChat,
  logout,
};
