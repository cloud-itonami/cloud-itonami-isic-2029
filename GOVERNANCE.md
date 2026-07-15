# Governance

`cloud-itonami-isic-2029` is an OSS open-business blueprint for other chemical products n.e.c. (illustrated concretely by adhesives/glues manufacturing) plant operations coordination.

## Maintainers
Maintainers may merge changes that preserve these invariants:
- a reactor/mixing-line-equipment action the governor refuses is never dispatched to hardware.
- the Adhesive Plant Operations Governor remains independent of the advisor.
- hard policy violations (equipment-control bypass, line actuation, chemical-safety-certification decision-making, record-suppression, unauthorized disclosure) cannot be overridden by human approval.
- every schedule, sign-off, record and disclose path is auditable.
- sensitive operating and personal data stays outside Git.

## Decision Records
Architecture decisions live in `docs/adr/`. Changes to the trust model, storage contract, public business model, operator certification or license should add or update an ADR.

## Operator Governance
Anyone may fork and operate independently. itonami.cloud certification is a separate trust mark and should require safety, audit and data-flow review.

Certified operators can lose certification for:
- bypassing reactor/mixing-line-control or record policy checks
- claiming or exercising chemical-safety-certification authority this actor does not have
- mishandling sensitive data
- misrepresenting certification status
- failing to respond to security or safety incidents
