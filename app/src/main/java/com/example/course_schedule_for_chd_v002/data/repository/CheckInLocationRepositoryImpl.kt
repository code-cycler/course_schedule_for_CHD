package com.example.course_schedule_for_chd_v002.data.repository

import com.example.course_schedule_for_chd_v002.data.local.database.CheckInLocationDao
import com.example.course_schedule_for_chd_v002.data.local.database.entity.CheckInLocationEntity
import com.example.course_schedule_for_chd_v002.domain.model.CheckInLocation
import com.example.course_schedule_for_chd_v002.domain.repository.ICheckInLocationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [v114] 签到位置仓库实现
 */
class CheckInLocationRepositoryImpl(
    private val dao: CheckInLocationDao
) : ICheckInLocationRepository {

    override fun observeAll(): Flow<List<CheckInLocation>> =
        dao.getAllFlow().map { list -> list.map { it.toDomainModel() } }

    override suspend fun getAll(): List<CheckInLocation> =
        dao.getAll().map { it.toDomainModel() }

    override suspend fun getById(id: Long): CheckInLocation? =
        dao.getById(id)?.toDomainModel()

    override suspend fun insert(location: CheckInLocation): Long =
        dao.insertAndGetId(CheckInLocationEntity.fromDomainModel(location))

    override suspend fun update(location: CheckInLocation) =
        dao.update(CheckInLocationEntity.fromDomainModel(location))

    override suspend fun deleteById(id: Long) = dao.deleteById(id)

    override suspend fun getByLinkedCourseOrRoom(
        courseName: String?,
        roomName: String?
    ): List<CheckInLocation> =
        dao.getByLinkedCourseOrRoom(courseName, roomName).map { it.toDomainModel() }
}
