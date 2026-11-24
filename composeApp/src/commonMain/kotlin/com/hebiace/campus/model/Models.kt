package com.hebiace.campus.model

import kotlinx.serialization.Serializable

// ============================================================
// 课表
// ============================================================

@Serializable
data class Course(
    val id: String = "",
    val name: String = "",
    val teacher: String = "",
    val location: String = "",
    val dayOfWeek: Int = 1,        // 1=周一 ... 7=周日
    val startSection: Int = 1,     // 起始节次
    val endSection: Int = 1,       // 结束节次
    val weeks: List<Int> = emptyList(), // 上课周次（空=每周）
    val colorIndex: Int = 0,
) {
    fun overlaps(other: Course): Boolean =
        dayOfWeek == other.dayOfWeek &&
            startSection <= other.endSection && other.startSection <= endSection
}

@Serializable
data class Semester(
    val year: Int = 2026,          // 学年起始年
    val term: Int = 1,             // 1=第一学期 2=第二学期
    val termStart: String = "",    // 开学日期 yyyy-MM-dd
    val totalWeeks: Int = 20,
)

@Serializable
data class SectionTime(
    val section: Int,
    val start: String,   // "08:20"
    val end: String,     // "09:05"
)

// ============================================================
// 作业 / 考试（学习通 + 雨课堂 统一）
// ============================================================

enum class TaskType { WORK, EXAM }
enum class TaskStatus { TODO, EXPIRED, SUBMITTED, GRADED }
enum class Provider { CHAOXING, YUKETANG, MANUAL }

@Serializable
data class Task(
    val key: String = "",
    val title: String = "",
    val courseName: String = "",
    val courseId: String = "",
    val classId: String = "",
    val type: TaskType = TaskType.WORK,
    val status: TaskStatus = TaskStatus.TODO,
    val endTime: Long = 0L,        // 截止时间（epoch millis）
    val publishTime: Long = 0L,    // 发布时间
    val grade: String = "",
    val url: String = "",
    val statusText: String = "",
    val provider: Provider = Provider.CHAOXING,
)

/** 课程入口（学习通/雨课堂），点击后进入对应平台学习页 */
@Serializable
data class CourseEntry(
    val provider: Provider = Provider.CHAOXING,
    val name: String = "",
    val courseId: String = "",
    val classId: String = "",
    val url: String = "",
)

// ============================================================
// 趣智校园（洗浴）
// ============================================================

@Serializable
data class QzxySession(
    val loginCode: String = "",
    val userId: String = "",
    val accountId: String = "",
    val projectId: String = "",
    val telephone: String = "",
    val name: String = "",
)

@Serializable
data class ShowerDevice(
    val deviceId: String = "",
    val deviceName: String = "",
    val snCode: String = "",
    val macAddress: String = "",
    val withholdMoney: Double = 0.0,
    val online: Boolean = true,
    val rssi: Int = 0,
)

@Serializable
data class ActiveShower(
    val snCode: String = "",
    val orderNo: String = "",
    val startAt: Long = 0L,        // 开阀时间 epoch millis
    val preDeductMoney: Double = 0.0,
    val autoStopSeconds: Int = 0,
)

@Serializable
data class QzxyWallet(
    val money: Double = 0.0,
    val realMoney: Double = 0.0,
    val givenMoney: Double = 0.0,
)

// ============================================================
// U净（洗衣）
// ============================================================

@Serializable
data class WasherProgram(
    val deviceId: String = "",
    val deviceNo: String = "",
    val deviceTypeName: String = "",
    val storeName: String = "",
    val createOrderEnabled: Boolean = false,
    val reason: String = "",
    val defaultModelId: Int = 0,
    val models: List<WasherModel> = emptyList(),
)

@Serializable
data class WasherModel(
    val id: Int = 0,
    val name: String = "",
    val priceFen: Int = 0,
    val timeMinutes: Int = 0,
)

@Serializable
data class WasherOrder(
    val orderId: String = "",
    val deviceNo: String = "",
    val status: String = "",
    val statusText: String = "",
    val payPrice: String = "",
    val remainSeconds: Int = 0,
    val countDownSeconds: Int = 0,
)
