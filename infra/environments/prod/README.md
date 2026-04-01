# Prod Environment Runbook

## 0) 사전 준비
- AWS CLI profile: `eks-role`
- region: `ap-northeast-2`
- Terraform: `1.5+`
- GitHub repository: `K-Stable/wearhouse-be`

OIDC( GitHub Actions -> AWS AssumeRoleWithWebIdentity )를 쓰는 경우, IAM Role trust policy `sub` 조건이 기존 repo면 배포가 실패합니다.
- 필수 반영값: `repo:K-Stable/wearhouse-be:*`
- 샘플 JSON: `infra/environments/prod/github-oidc-trust-policy.example.json`

## 1) 값 파일 준비
```bash
cd infra/environments/prod
cp terraform.tfvars.example terraform.tfvars
cp backend.hcl.example backend.hcl
```

`terraform.tfvars`에서 최소 아래 값은 꼭 채우세요.
- `db_password`
- `ssm_secure_parameters` 내부 민감 값

## 2) 초기화 + 검증
```bash
terraform init -backend-config=backend.hcl
terraform validate
```

## 3) 계획/적용
```bash
terraform plan -out=tfplan
terraform apply tfplan
```

## 4) 배포 후 확인
```bash
aws eks update-kubeconfig --name wearhouse-eks --region ap-northeast-2 --profile eks-role
kubectl get nodes
kubectl -n kube-system get pods | grep -E 'aws-load-balancer-controller|metrics-server'
kubectl -n argocd get pods
```

## 5) 앱 배포(Argo CD)
Terraform은 Argo CD까지 설치합니다. 앱은 Argo CD `Application`(Helm chart 기준)으로 배포하세요.
- 외부 공개는 `api-gateway` Ingress만 생성
- 나머지 서비스는 ClusterIP 유지

Argo CD에서 Git repo를 직접 연결 중이면 `repoURL`도 새 저장소로 바꾸세요.
- `https://github.com/K-Stable/wearhouse-be.git`

## 6) 파괴
```bash
terraform destroy
```

운영에서는 `deletion_protection`, `skip_final_snapshot` 값을 환경 정책에 맞게 조정하세요.
