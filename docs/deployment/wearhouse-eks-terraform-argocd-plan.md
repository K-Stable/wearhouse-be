# Wearhouse EKS 배포 계획 (Terraform + Argo CD + Helm)

## 1. 목표
- Terraform으로 AWS 인프라 일괄 관리
- EKS OIDC(IRSA) 기반 권한 분리
- AWS Load Balancer Controller + Ingress로 `api-gateway`만 외부 공개
- Argo CD + Helm 기반 GitOps 배포
- RDS(MySQL), MSK(Kafka), SSM Parameter Store 사용
- Probe(readiness/liveness/startup), graceful shutdown, HPA 반영

## 2. 네이밍 컨벤션
- Prefix: `wearhouse-`
- VPC: `wearhouse-vpc`
- 서브넷: `wearhouse-public-a/b/c`, `wearhouse-private-a/b/c`, `wearhouse-data-a/b/c`
- 라우팅: `wearhouse-rt-public`, `wearhouse-rt-private`, `wearhouse-rt-data`
- EKS: `wearhouse-eks`

## 3. 단계별 실행

### Phase 0: 준비/정리
1. 기존 EKS 관련 리소스만 정리(오탐 삭제 방지)
2. AWS profile/region 고정 (`eks-role`, `ap-northeast-2`)
3. Terraform backend(S3 + DynamoDB lock) 준비

### Phase 1: 네트워크 + EKS
1. VPC/서브넷/NAT/라우팅 생성
2. EKS 클러스터 및 amd64 node group 생성
3. OIDC(IRSA) 활성화

### Phase 2: 플랫폼 애드온
1. metrics-server 설치
2. AWS Load Balancer Controller Helm 설치(IRSA role 연결)
3. Argo CD Helm 설치

### Phase 3: 데이터 계층
1. RDS(MySQL) 생성
2. MSK(Kafka) 생성
3. 보안 그룹 정책(EKS node -> RDS/MSK) 적용

### Phase 4: 애플리케이션 전달
1. ECR 레포 생성
2. 서비스 이미지 `linux/amd64` 빌드/푸시
3. Helm chart/values 정리
4. Argo CD Application으로 배포

### Phase 5: 운영 안정화
1. readiness/liveness/startup probe 점검
2. graceful shutdown (`preStop`, `terminationGracePeriodSeconds`) 적용
3. HPA 정책 서비스별 적용
4. SSM 파라미터 연동 및 비밀값 주입 전략 확정

## 4. 오래 걸렸던 주요 원인(재발 방지)
1. 이미지 아키텍처 불일치(`no match for platform in manifest`)로 ImagePullBackOff 재시도 루프 발생
2. Kafka 초기 설정/자원값 미세 조정 시간 소요
3. 삭제 범위 합의(eks-only vs 기타 리소스) 재정렬 필요

## 5. 이번 코드 기준 완료 상태
- Terraform 폴더/모듈 골격 완성
- `terraform init/validate` 검증 완료
- OIDC(IRSA) + ALB Controller + Argo CD 설치 코드 반영
- 네이밍 컨벤션 `wearhouse-*` 반영

## 6. 다음 실행(실행 명령)
```bash
cd infra/environments/prod
cp terraform.tfvars.example terraform.tfvars
cp backend.hcl.example backend.hcl
# terraform.tfvars에 db_password 및 secure params 입력
terraform init -backend-config=backend.hcl
terraform plan -out=tfplan
terraform apply tfplan
```
