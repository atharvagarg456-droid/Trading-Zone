package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AbusiveWordModerator
import com.example.data.model.UserProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class TradingRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun submitUserRegistration_createsAndReadsOwnProfile() = runBlocking {
        val email = "trader_${UUID.randomUUID().toString().take(6)}@example.com"
        signInTestUser(email)
        val repo = TradingRepository(firestore)
        val devId = "DEV_${UUID.randomUUID().toString().take(8)}"

        val result = withTimeout(5000L) {
            repo.submitUserRegistration(
                email = email,
                fullName = "Trader One",
                phoneNumber = "9876543210",
                city = "Mumbai",
                photoBase64 = "data:image/jpeg;base64,TEST",
                deviceId = devId,
                deviceModel = "Pixel 9",
                ipAddress = "10.0.0.1",
                securityQuestion = "Favorite commodity?",
                securityAnswer = "Gold",
                termsAccepted = true,
                isCallerAdmin = false,
                appLockPin = "1234"
            )
        }

        assertTrue("Expected registration success but got: ${result.exceptionOrNull()}", result.isSuccess)
    }

    @Test
    fun requestProfileEditAndReadReceipt_memberCanSubmitEditAndReceipt() = runBlocking {
        val email = "member_${UUID.randomUUID().toString().take(6)}@example.com"
        val uid = signInTestUser(email)
        val repo = TradingRepository(firestore)
        val devId = "DEV_${UUID.randomUUID().toString().take(8)}"

        val regResult = withTimeout(5000L) {
            repo.submitUserRegistration(
                email = email,
                fullName = "Verified Member",
                phoneNumber = "9988776655",
                city = "Delhi",
                photoBase64 = "data:image/jpeg;base64,MEMBER",
                deviceId = devId,
                deviceModel = "Pixel 9 Pro",
                ipAddress = "10.0.0.2",
                securityQuestion = "Favorite market?",
                securityAnswer = "MCX",
                termsAccepted = true,
                isCallerAdmin = false,
                appLockPin = "4321"
            )
        }
        assertTrue("Expected registration success: ${regResult.exceptionOrNull()}", regResult.isSuccess)

        val editResult = withTimeout(5000L) {
            repo.requestProfileEdit(
                newFullName = "Updated Member Name",
                newPhoneNumber = "9988776655",
                newCity = "Bengaluru",
                newPhotoBase64 = "data:image/jpeg;base64,UPDATED",
                isCallerAdmin = false
            )
        }
        assertTrue("Expected profile edit request success: ${editResult.exceptionOrNull()}", editResult.isSuccess)

        val receiptResult = withTimeout(5000L) {
            repo.recordMessageReadReceipt(
                messageId = "msg_${UUID.randomUUID().toString().take(8)}",
                profile = UserProfile(
                    userId = uid,
                    email = email,
                    fullName = "Verified Member",
                    photoBase64 = "data:image/jpeg;base64,MEMBER"
                )
            )
        }
        assertTrue("Expected read receipt success: ${receiptResult.exceptionOrNull()}", receiptResult.isSuccess)
    }

    @Test
    fun observeBroadcastMessages_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = TradingRepository(firestore)

        var caught: Throwable? = null
        try {
            withTimeout(3000L) {
                repo.observeBroadcastMessages().first()
            }
        } catch (e: Throwable) {
            caught = e
        }
        assertNotNull("Expected unauthenticated query to throw", caught)
    }

    @Test
    fun abusiveWordModerator_blocksPhoneNumbersEmailsAndPersonalIds() {
        val phoneAttempt = AbusiveWordModerator.checkProhibitedContactOrId("Call me at 9876543210 for trades")
        assertTrue(phoneAttempt.isBlocked)

        val emailAttempt = AbusiveWordModerator.checkProhibitedContactOrId("Reach me at user@gmail.com")
        assertTrue(emailAttempt.isBlocked)

        val cleanCommodityText = AbusiveWordModerator.checkProhibitedContactOrId("Gold MCX looking strong near 72500 support")
        assertFalse(cleanCommodityText.isBlocked)
    }
}
