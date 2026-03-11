export const statusBadgeClass: Record<string, string> = {
  PENDING_PAYMENT: 'status-pending',
  AWAITING_COACH:  'status-awaiting',
  CONFIRMED:       'status-approved',
  REJECTED:        'status-rejected',
  CANCELLED:       'status-cancelled',
};

export const statusLabel: Record<string, string> = {
  PENDING_PAYMENT: 'Awaiting Payment',
  AWAITING_COACH:  'Paid – Awaiting Coach',
  CONFIRMED:       'Confirmed',
  REJECTED:        'Rejected',
  CANCELLED:       'Cancelled',
};
