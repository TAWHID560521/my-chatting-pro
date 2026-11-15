package com.example.core.network

import com.example.core.model.AuthResponse
import com.example.core.model.BlockUserRequest
import com.example.core.model.CompleteUploadRequest
import com.example.core.model.ConversationDto
import com.example.core.model.ConversationsListResponse
import com.example.core.model.CreateConversationRequest
import com.example.core.model.DeleteAccountRequest
import com.example.core.model.GenericOkResponse
import com.example.core.model.IceServersResponse
import com.example.core.model.LoginRequest
import com.example.core.model.MessageDto
import com.example.core.model.MessagesPageResponse
import com.example.core.model.NotificationEndpointRequest
import com.example.core.model.PresignDownloadResponse
import com.example.core.model.PresignUploadRequest
import com.example.core.model.PresignUploadResponse
import com.example.core.model.RecoveryInfoResponse
import com.example.core.model.RefreshTokenRequest
import com.example.core.model.RegisterRequest
import com.example.core.model.ReportRequest
import com.example.core.model.SearchUserRequest
import com.example.core.model.SearchUserResponse
import com.example.core.model.SendMessageRequest
import com.example.core.model.UpdateProfileRequest
import com.example.core.model.UserProfileDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @POST("api/v1/auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<AuthResponse>

    @POST("api/v1/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<AuthResponse>

    @POST("api/v1/auth/refresh")
    suspend fun refreshToken(@Body body: RefreshTokenRequest): Response<AuthResponse>

    @POST("api/v1/auth/logout")
    suspend fun logout(): Response<GenericOkResponse>

    @GET("api/v1/auth/recovery-info")
    suspend fun getRecoveryInfo(): Response<RecoveryInfoResponse>

    @HTTP(method = "DELETE", path = "api/v1/account", hasBody = true)
    suspend fun deleteAccount(@Body body: DeleteAccountRequest): Response<GenericOkResponse>

    @GET("api/v1/users/me")
    suspend fun getMyProfile(): Response<UserProfileDto>

    @PUT("api/v1/users/me")
    suspend fun updateMyProfile(@Body body: UpdateProfileRequest): Response<UserProfileDto>

    @POST("api/v1/users/search")
    suspend fun searchUser(@Body body: SearchUserRequest): Response<SearchUserResponse>

    @POST("api/v1/users/block")
    suspend fun blockUser(@Body body: BlockUserRequest): Response<GenericOkResponse>

    @POST("api/v1/users/unblock")
    suspend fun unblockUser(@Body body: BlockUserRequest): Response<GenericOkResponse>

    @POST("api/v1/reports")
    suspend fun submitReport(@Body body: ReportRequest): Response<GenericOkResponse>

    @GET("api/v1/conversations")
    suspend fun getConversations(): Response<ConversationsListResponse>

    @POST("api/v1/conversations")
    suspend fun createOrGetConversation(@Body body: CreateConversationRequest): Response<ConversationDto>

    @GET("api/v1/conversations/{conversationId}/messages")
    suspend fun getMessages(
        @Path("conversationId") conversationId: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 30
    ): Response<MessagesPageResponse>

    @POST("api/v1/conversations/{conversationId}/messages")
    suspend fun sendMessage(
        @Path("conversationId") conversationId: String,
        @Body body: SendMessageRequest
    ): Response<MessageDto>

    @POST("api/v1/conversations/{conversationId}/read")
    suspend fun markConversationRead(
        @Path("conversationId") conversationId: String
    ): Response<GenericOkResponse>

    @DELETE("api/v1/messages/{messageId}")
    suspend fun deleteOwnMessage(
        @Path("messageId") messageId: String
    ): Response<GenericOkResponse>

    @POST("api/v1/media/presign-upload")
    suspend fun presignUpload(@Body body: PresignUploadRequest): Response<PresignUploadResponse>

    @POST("api/v1/media/complete-upload")
    suspend fun completeUpload(@Body body: CompleteUploadRequest): Response<GenericOkResponse>

    @GET("api/v1/media/{mediaObjectId}/presign-download")
    suspend fun presignDownload(
        @Path("mediaObjectId") mediaObjectId: String
    ): Response<PresignDownloadResponse>

    @GET("api/v1/calls/ice-servers")
    suspend fun getIceServers(): Response<IceServersResponse>

    @POST("api/v1/notifications/endpoint")
    suspend fun registerNotificationEndpoint(
        @Body body: NotificationEndpointRequest
    ): Response<GenericOkResponse>
}
