# SlickHood smart-gate integration

SlickHood is the access-policy and audit system. A gate controller must retain certified obstruction detection,
emergency release, fire-mode behaviour, local safety interlocks, and an attended manual override. A `GRANTED`
decision is permission to proceed; it is not a motor-control command.

## Device identity

Each controller generates an Ed25519 key pair locally and keeps its private key in secure hardware where available.
An authorised property or estate manager registers the Base64 X.509 public key with `POST /smart-gate/devices`.
SlickHood returns a non-secret `deviceCode`. Private keys are never uploaded.

## Access decision

Send the exact JSON request body to `POST /smart-gate/device/access-decision` with:

- `X-Gate-Device`: registered device code
- `X-Gate-Timestamp`: current Unix timestamp in seconds
- `X-Gate-Nonce`: cryptographically random value of at least 16 characters, never reused
- `X-Gate-Signature`: Base64 Ed25519 signature

Sign these UTF-8 bytes exactly: `timestamp + "\n" + nonce + "\n" + rawJsonBody`.

```json
{
  "accessCode": "the QR or text credential",
  "direction": "ENTRY",
  "vehiclePlate": "KDA 123A",
  "correlationId": "controller-unique-event-id"
}
```

Requests outside the two-minute clock window, repeated nonces, invalid signatures, disabled devices, expired
credentials, wrong properties, vehicle mismatches, anti-passback violations, and exhausted entry limits are denied.
The correlation ID makes retries idempotent and every granted or denied evaluation is retained as an access event.

Controllers should fail closed when SlickHood is unreachable. Any future offline grant format must be separately
versioned, short-lived, property-scoped, signed by a rotating SlickHood offline key, and backed by a revocation sync.
