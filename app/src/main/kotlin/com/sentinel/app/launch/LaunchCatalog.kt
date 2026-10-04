package com.sentinel.app.launch

object LaunchCatalog {
    // Update verification only with a recorded commercial-ad baseline and real-device comparisons.
    val policy = LaunchPolicy(listOf(
        LaunchProfile("cainiao", "com.cainiao.wireless", "菜鸟", 475, "8.11.923",
            "com.cainiao.wireless.homepage.view.activity.HomePageActivity", null, true,
            "冷启动；首轮设备：华为 OCE_AN50", "2026-10-04：三组普通入口均有商业广告；候选均进入首页。快递列表为空，物流详情由用户补验"),
        LaunchProfile("yangshipin", "com.cctv.yangshipin.app.androidp", "央视频", 305030, "3.5.3.26910",
            "com.tencent.videolite.android.component.literoute.OpenActivity", "cctvvideo://cctv.com/HomeActivity?from=third_h5", false,
            "冷启动；首轮设备：华为 OCE_AN50", "候选仍出现首页弹窗广告，暂不开放创建"),
    ))
}
