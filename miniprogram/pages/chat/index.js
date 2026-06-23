const { request } = require("../../utils/request");
const { formatDateTime } = require("../../utils/format");

Page({
  data: {
    activeFamily: null,
    messages: [],
    inputValue: "",
    sending: false,
    error: "",
  },

  onShow() {
    const app = getApp();
    if (!app.getSession()) {
      wx.reLaunch({ url: "/pages/login/index" });
      return;
    }
    const activeFamily = app.getActiveFamily();
    if (!activeFamily) {
      wx.switchTab({ url: "/pages/families/index" });
      return;
    }
    this.setData({
      activeFamily,
    });
  },

  handleInput(event) {
    this.setData({
      inputValue: event.detail.value,
    });
  },

  handleSend() {
    const message = this.data.inputValue.trim();
    if (!message || this.data.sending || !this.data.activeFamily) {
      return;
    }

    const nextMessages = this.data.messages.concat({
      role: "user",
      content: message,
      time: formatDateTime(new Date().toISOString()),
    });

    this.setData({
      messages: nextMessages,
      inputValue: "",
      sending: true,
      error: "",
    });

    request({
      url: "/agent/chat",
      method: "POST",
      data: {
        familyId: this.data.activeFamily.id,
        message,
        history: nextMessages.slice(-10).map((item) => ({
          role: item.role,
          content: item.content,
        })),
        client_timestamp: new Date().toISOString(),
      },
    }).then((response) => {
      this.setData({
        messages: this.data.messages.concat({
          role: "assistant",
          content: response.content,
          time: formatDateTime(new Date().toISOString()),
          canSave: true,
        }),
      });
    }).catch((error) => {
      this.setData({
        error: error.message || "发送失败，请稍后重试",
      });
    }).finally(() => {
      this.setData({
        sending: false,
      });
    });
  },

  handleSaveMessage(event) {
    const index = Number(event.currentTarget.dataset.index);
    const message = this.data.messages[index];
    if (!message || !message.content || !this.data.activeFamily) {
      return;
    }

    request({
      url: "/memories/family",
      method: "POST",
      data: {
        familyId: this.data.activeFamily.id,
        content: message.content,
      },
    }).then(() => {
      wx.showToast({
        title: "已保存到家庭记忆",
        icon: "success",
      });
    }).catch((error) => {
      this.setData({
        error: error.message || "保存记忆失败",
      });
    });
  },

  goFamilies() {
    wx.switchTab({
      url: "/pages/families/index",
    });
  },

  goMemories() {
    wx.switchTab({
      url: "/pages/memories/index",
    });
  },
});
