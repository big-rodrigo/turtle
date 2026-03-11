const BASE_URL = 'http://localhost:8080';

export type UserRole = 'CLIENT' | 'COACH' | 'COACH_PENDING' | 'ADMIN';
export type CoachStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export type BookingStatus = 'PENDING_PAYMENT' | 'AWAITING_COACH' | 'CONFIRMED' | 'REJECTED' | 'CANCELLED';
export type PaymentStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED' | 'REFUNDED' | 'IN_MEDIATION';

export interface ApiError {
	message: string;
	errors?: string[];
}

export interface TokenResponse {
	token: string;
}

export interface JwtPayload {
	iss: string;
	sub: string;
	groups: UserRole | UserRole[];
	exp: number;
}

export interface CoachSummary {
	id: number;
	name: string;
	specialty: string;
}

export type AvailabilityStatus = 'AVAILABLE' | 'BOOKED' | 'EXPIRED';

export interface AvailabilitySlot {
	id: number;
	startsAt: string;
	endsAt: string;
	status: AvailabilityStatus;
	serviceId: number | null;
	serviceName: string | null;
}

export interface ExtraServiceSummary {
	id: number;
	name: string;
	description: string | null;
}

export interface CoachingServiceResponse {
	id: number;
	coachId: number;
	name: string;
	description: string | null;
	extras: ExtraServiceSummary[];
}

export interface CoachingServiceRequest {
	name: string;
	description?: string;
	extraServiceIds?: number[];
}

export interface TimeWindowRequest {
	startDate: string;
	endDate: string;
	dailyStartTime: string;
	dailyEndTime: string;
	unitOfWorkMinutes: number;
	pricePerUnit?: number;
	priority: number;
	serviceId: number;
}

export interface TimeWindowResponse {
	id: number;
	startDate: string;
	endDate: string;
	dailyStartTime: string;
	dailyEndTime: string;
	unitOfWorkMinutes: number;
	pricePerUnit?: number;
	priority: number;
	serviceId: number | null;
	serviceName: string | null;
}

export interface BookingResourceResponse {
	id: number;
	title: string;
	url: string;
	description: string | null;
	createdAt: string;
}

export interface BookingResponse {
	id: number;
	clientId: number;
	clientName: string;
	coachId: number;
	coachName: string;
	availabilityIds: number[];
	startsAt: string;
	endsAt: string;
	status: BookingStatus;
	paymentStatus: PaymentStatus | null;
	notes: string | null;
	createdAt: string;
	extras: ExtraServiceSummary[];
	serviceId: number | null;
	serviceName: string | null;
	resources: BookingResourceResponse[];
}

export interface PaymentPreferenceResponse {
	preferenceId: string;
	checkoutUrl: string;
	free: boolean;
}

export interface ChatMessage {
	id: number;
	senderId: number;
	senderName: string;
	content: string;
	sentAt: string;
}

export interface CoachStatusResponse {
	userId: number;
	name: string;
	email: string;
	specialty: string;
	status: CoachStatus;
}

export type SocialLinkType = 'INSTAGRAM' | 'TWITTER' | 'LINKEDIN' | 'YOUTUBE' | 'TIKTOK' | 'FACEBOOK' | 'CUSTOM';

export interface SocialLink {
	id: number;
	type: SocialLinkType;
	url: string;
	label: string | null;
}

export interface CoachProfile {
	id: number;
	name: string;
	specialty: string;
	description: string | null;
	pictureUrl: string | null;
	socialLinks: SocialLink[];
}

export interface ClientProfile {
	id: number;
	name: string;
	description: string | null;
	socialLinks: SocialLink[];
}

export interface UpdateCoachProfileRequest {
	description?: string;
	specialty?: string;
	pictureUrl?: string;
	socialLinks: { type: SocialLinkType; url: string; label?: string }[];
}

export interface UpdateClientProfileRequest {
	description?: string;
	socialLinks: { type: SocialLinkType; url: string; label?: string }[];
}

class ApiClient {
	private token: string | null = null;

	setToken(token: string | null) {
		this.token = token;
	}

	private headers(extra?: Record<string, string>): Record<string, string> {
		const h: Record<string, string> = { 'Content-Type': 'application/json', ...extra };
		if (this.token) h['Authorization'] = `Bearer ${this.token}`;
		return h;
	}

	private async request<T>(method: string, path: string, body?: unknown): Promise<T> {
		const res = await fetch(`${BASE_URL}${path}`, {
			method,
			headers: this.headers(),
			body: body !== undefined ? JSON.stringify(body) : undefined
		});

		if (res.status === 204) return undefined as T;

		const data = await res.json();
		if (!res.ok) throw data as ApiError;
		return data as T;
	}

	// Auth
	register(payload: {
		name: string;
		email: string;
		phone?: string;
		password: string;
		role: 'CLIENT' | 'COACH';
	}) {
		return this.request<TokenResponse>('POST', '/auth/register', payload);
	}

	login(payload: { email: string; password: string }) {
		return this.request<TokenResponse>('POST', '/auth/login', payload);
	}

	createAdmin(payload: { name: string; email: string; password: string; provisioningToken: string }) {
		return this.request<TokenResponse>('POST', '/auth/admin', payload);
	}

	// Coaches
	getCoaches() {
		return this.request<CoachSummary[]>('GET', '/coaches');
	}

	getTimeWindows(coachId: number) {
		return this.request<TimeWindowResponse[]>('GET', `/coaches/${coachId}/time-windows`);
	}

	createTimeWindow(coachId: number, payload: TimeWindowRequest) {
		return this.request<TimeWindowResponse>('POST', `/coaches/${coachId}/time-windows`, payload);
	}

	deleteTimeWindow(windowId: number) {
		return this.request<void>('DELETE', `/coaches/time-windows/${windowId}`);
	}

	reorderTimeWindows(coachId: number, updates: { id: number; priority: number }[]) {
		return this.request<void>('PATCH', `/coaches/${coachId}/time-windows/reorder`, updates);
	}

	getSlotsByDate(coachId: number, date: string) {
		return this.request<AvailabilitySlot[]>('GET', `/coaches/${coachId}/slots?date=${date}`);
	}

	getCoachingServices(coachId: number) {
		return this.request<CoachingServiceResponse[]>('GET', `/coaches/${coachId}/services`);
	}

	createCoachingService(coachId: number, payload: CoachingServiceRequest) {
		return this.request<CoachingServiceResponse>('POST', `/coaches/${coachId}/services`, payload);
	}

	updateCoachingService(serviceId: number, payload: CoachingServiceRequest) {
		return this.request<CoachingServiceResponse>('PATCH', `/services/${serviceId}`, payload);
	}

	deleteCoachingService(serviceId: number) {
		return this.request<void>('DELETE', `/services/${serviceId}`);
	}

	getSlotsByService(serviceId: number, date: string) {
		return this.request<AvailabilitySlot[]>('GET', `/services/${serviceId}/slots?date=${date}`);
	}

	// Bookings
	getBookings() {
		return this.request<BookingResponse[]>('GET', '/bookings');
	}

	getBooking(id: number) {
		return this.request<BookingResponse>('GET', `/bookings/${id}`);
	}

	createBooking(payload: { availabilityIds: number[]; serviceId: number; notes?: string; extraServiceIds?: number[] }) {
		return this.request<BookingResponse>('POST', '/bookings', payload);
	}

	confirmBooking(id: number) {
		return this.request<BookingResponse>('PATCH', `/bookings/${id}/confirm`);
	}

	createPaymentPreference(bookingId: number) {
		return this.request<PaymentPreferenceResponse>('POST', `/bookings/${bookingId}/payment/preference`);
	}

	rejectBooking(id: number) {
		return this.request<BookingResponse>('PATCH', `/bookings/${id}/reject`);
	}

	cancelBooking(id: number) {
		return this.request<void>('DELETE', `/bookings/${id}`);
	}

	addBookingResource(bookingId: number, payload: { title: string; url: string; description?: string }) {
		return this.request<BookingResourceResponse>('POST', `/bookings/${bookingId}/resources`, payload);
	}

	getBookingResources(bookingId: number) {
		return this.request<BookingResourceResponse[]>('GET', `/bookings/${bookingId}/resources`);
	}

	deleteBookingResource(bookingId: number, resourceId: number) {
		return this.request<void>('DELETE', `/bookings/${bookingId}/resources/${resourceId}`);
	}

	// Chat
	getMessages(bookingId: number) {
		return this.request<ChatMessage[]>('GET', `/bookings/${bookingId}/messages`);
	}

	sendMessage(bookingId: number, content: string) {
		return this.request<ChatMessage>('POST', `/bookings/${bookingId}/messages`, { content });
	}

	// Profiles
	getCoachProfile(id: number) {
		return this.request<CoachProfile>('GET', `/coaches/${id}`);
	}

	updateCoachProfile(id: number, data: UpdateCoachProfileRequest) {
		return this.request<CoachProfile>('PUT', `/coaches/${id}/profile`, data);
	}

	getClientProfile(id: number) {
		return this.request<ClientProfile>('GET', `/clients/${id}`);
	}

	updateClientProfile(id: number, data: UpdateClientProfileRequest) {
		return this.request<ClientProfile>('PUT', `/clients/${id}/profile`, data);
	}

	// Admin
	getAdminCoaches(status?: CoachStatus) {
		const query = status ? `?status=${status}` : '';
		return this.request<CoachStatusResponse[]>('GET', `/admin/coaches${query}`);
	}

	approveCoach(userId: number) {
		return this.request<CoachStatusResponse>('PATCH', `/admin/coaches/${userId}/approve`);
	}

	rejectCoach(userId: number) {
		return this.request<CoachStatusResponse>('PATCH', `/admin/coaches/${userId}/reject`);
	}
}

export const api = new ApiClient();
