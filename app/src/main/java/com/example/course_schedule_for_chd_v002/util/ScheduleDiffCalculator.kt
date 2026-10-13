package com.example.course_schedule_for_chd_v002.util

import com.example.course_schedule_for_chd_v002.domain.model.Course

/**
 * [v125] 课表差异（静默同步，L2-build D 批第 ② 步）
 *
 * @param added 教务新增（远端有、本地无，且无同名旧记录可配对）
 * @param removed 教务删除（本地有、远端无，且无同名新记录可配对）——含用户手动添加的本地独有课程，
 *   diff 无法自动区分「教务删了」与「本地自加」，由确认对话框统一打「本地独有」标（grill-W01 Q3）
 * @param changed 同名课程内容变更（旧, 新）配对
 */
data class ScheduleDiff(
    val added: List<Course>,
    val removed: List<Course>,
    val changed: List<Pair<Course, Course>>
) {
    val isEmpty: Boolean get() = added.isEmpty() && removed.isEmpty() && changed.isEmpty()
    val totalCount: Int get() = added.size + removed.size + changed.size
}

/**
 * [v125] 课程级三态 diff（纯函数零 IO，L1 契约第 2 条）。
 *
 * 比对键 = 除 id 外的全部业务字段（名称/教师/教室/星期/周次范围/节次/类型/学分/remark 含周位图）——
 * **必须忽略 id**：本地 Course.id 是 Room 自增，远端解析出的 id 恒为 0，带 id 比较会把所有课程
 * 判成「删除+新增」。"changed" 按课程名配对（同名其余字段异 = 变更，多教学班同名多条逐条 zip 配对）。
 */
object ScheduleDiffCalculator {

    private fun Course.businessKey(): String {
        return "$name|$teacher|$location|$dayOfWeek|$startWeek|$endWeek|$startNode|$endNode|$courseType|$credit|$remark"
    }

    fun computeDiff(local: List<Course>, remote: List<Course>): ScheduleDiff {
        if (local.isEmpty()) {
            return if (remote.isEmpty()) ScheduleDiff(emptyList(), emptyList(), emptyList())
            else ScheduleDiff(remote, emptyList(), emptyList())
        }
        if (remote.isEmpty()) {
            return ScheduleDiff(emptyList(), local, emptyList())
        }

        val localKeys = local.map { it.businessKey() }.toHashSet()
        val remoteKeys = remote.map { it.businessKey() }.toHashSet()

        val removedAll = local.filter { it.businessKey() !in remoteKeys }
        val addedAll = remote.filter { it.businessKey() !in localKeys }

        // 同名配对为 changed（多教学班同名多条 zip 配对，剩余不配对的才是真新增/真删除）
        val removedByName = removedAll.groupBy { it.name }
        val addedByName = addedAll.groupBy { it.name }
        val commonNames = removedByName.keys intersect addedByName.keys

        val changed = mutableListOf<Pair<Course, Course>>()
        val pairedRemoved = mutableSetOf<Course>()
        val pairedAdded = mutableSetOf<Course>()
        for (name in commonNames) {
            val olds = removedByName.getValue(name)
            val news = addedByName.getValue(name)
            val pairCount = minOf(olds.size, news.size)
            for (i in 0 until pairCount) {
                changed.add(olds[i] to news[i])
                pairedRemoved.add(olds[i])
                pairedAdded.add(news[i])
            }
        }

        val realAdded = addedAll.filter { it !in pairedAdded }
        val realRemoved = removedAll.filter { it !in pairedRemoved }

        return ScheduleDiff(realAdded, realRemoved, changed)
    }
}
