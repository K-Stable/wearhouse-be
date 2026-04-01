# Wearhouse Terraform Infrastructure

이 디렉터리는 `wearhouse` 백엔드의 AWS 인프라를 Terraform으로 관리하기 위한 코드입니다.

## 목표 아키텍처
- 네이밍 컨벤션: `wearhouse-*`
- 네트워크: `public/private/data` 서브넷을 AZ `a/b/c`로 분리
- EKS: OIDC(IRSA) 활성화, amd64 노드 그룹
- Ingress: AWS Load Balancer Controller (ALB)
- GitOps: Argo CD (Helm으로 설치)
- 데이터: RDS(MySQL), MSK(Kafka)
- 컨테이너 레지스트리: ECR
- 비밀/런타임 설정: SSM Parameter Store

## 폴더 구조
- `environments/prod`: 실제 배포 진입점
- `modules/*`: 재사용 가능한 인프라 모듈
- `policies`: 커스텀 IAM 정책(필요 시)

## 빠른 시작
1. `cd infra/environments/prod`
2. `cp terraform.tfvars.example terraform.tfvars`
3. `cp backend.hcl.example backend.hcl`
4. 값 수정 (`profile`, `region`, CIDR, 비밀번호 등)
5. `terraform init -backend-config=backend.hcl`
6. `terraform plan`
7. `terraform apply`

## GitHub Repo 이전 반영 체크
- 현재 기준 백엔드 저장소: `https://github.com/K-Stable/wearhouse-be`
- GitHub Actions OIDC를 쓰는 경우, AWS IAM Role trust policy의 `sub` 조건을 새 repo로 바꿔야 합니다.
  - 예: `repo:K-Stable/wearhouse-be:*`
- 샘플 trust policy는 `infra/environments/prod/github-oidc-trust-policy.example.json`에 추가되어 있습니다.

## 상태 저장소(backend) 설명
`S3 + DynamoDB` 조합의 DynamoDB는 **애플리케이션 DB가 아니라 Terraform 상태 잠금(lock)** 용도입니다.
MySQL/RDS와 역할이 완전히 다릅니다.

원하면 DynamoDB 없이도 운영은 가능하지만, 동시 실행 시 상태 충돌 위험이 커져서 운영 환경에서는 잠금 테이블 사용을 권장합니다.

## 배포 순서 권장
1. 네트워크 + EKS + OIDC + ECR + RDS + MSK + SSM
2. AWS Load Balancer Controller / Argo CD 설치
3. 앱 Helm chart를 Argo CD Application으로 배포
4. `api-gateway`만 Ingress/ALB로 외부 공개

## 주의
- EKS 이미지 빌드/푸시는 반드시 `linux/amd64`로 고정하세요.
- 민감정보는 `terraform.tfvars`에 직접 커밋하지 말고, SSM/Secrets Manager를 사용하세요.
