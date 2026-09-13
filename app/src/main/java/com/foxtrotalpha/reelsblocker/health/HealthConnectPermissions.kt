package com.foxtrotalpha.reelsblocker.health

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord

object HealthConnectPermissions {

    fun requiredPermissions(): Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
    )

    fun isHealthConnectAvailable(context: Context): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    fun createPermissionContract(): ActivityResultContract<Set<String>, Set<String>> {
        return PermissionController.createRequestPermissionResultContract()
    }

    suspend fun getGrantedPermissions(client: HealthConnectClient): Set<String> {
        return client.permissionController.getGrantedPermissions()
    }

    suspend fun hasAnyPermission(client: HealthConnectClient): Boolean {
        val granted = getGrantedPermissions(client)
        return requiredPermissions().any { it in granted }
    }

    suspend fun hasStepsPermission(client: HealthConnectClient): Boolean {
        return HealthPermission.getReadPermission(StepsRecord::class) in getGrantedPermissions(client)
    }

    suspend fun hasSleepPermission(client: HealthConnectClient): Boolean {
        return HealthPermission.getReadPermission(SleepSessionRecord::class) in getGrantedPermissions(client)
    }

    suspend fun hasActiveCaloriesPermission(client: HealthConnectClient): Boolean {
        return HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) in
            getGrantedPermissions(client)
    }

    suspend fun hasTotalCaloriesPermission(client: HealthConnectClient): Boolean {
        return HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class) in
            getGrantedPermissions(client)
    }
}
