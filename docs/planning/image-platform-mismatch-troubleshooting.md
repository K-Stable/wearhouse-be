# Image Platform Mismatch Troubleshooting

`ImagePullBackOff` + `no match for platform in manifest` 오류 대응 문서입니다.

## 1. 증상

- 파드 상태가 `Pending` 또는 `ImagePullBackOff`로 유지됩니다.
- `kubectl describe pod` 이벤트에 아래 메시지가 보입니다.

```text
Failed to pull image "...": no match for platform in manifest: not found
Back-off pulling image "..."
```

## 2. 의미

이 오류는 애플리케이션 코드 실행 전에 실패합니다.

- 노드가 요구하는 플랫폼(예: `linux/amd64`)용 이미지가 해당 태그에 없습니다.
- 태그는 존재해도, 필요한 아키텍처 매니페스트가 없으면 pull 실패합니다.

## 3. 주요 원인

1. ARM(M1/M2) 환경에서 기본 빌드되어 `linux/arm64`만 푸시됨
2. EKS 노드는 `linux/amd64`인데 단일 아키텍처 이미지만 존재
3. 같은 태그를 다른 아키텍처로 재푸시해 manifest가 의도와 다르게 덮어써짐
4. `buildx` 사용 시 `--load`/`--push` 조합 실수로 단일 아키텍처만 올라감

## 4. 빠른 진단 절차

### 4.1 실패 파드 이벤트 확인

```bash
kubectl -n wearhouse get pods
kubectl -n wearhouse describe pod <pod-name>
```

### 4.2 어떤 태그를 쓰는지 확인

```bash
kubectl -n wearhouse get deploy \
  -o 'custom-columns=NAME:.metadata.name,IMAGE:.spec.template.spec.containers[*].image'
```

### 4.3 이미지 플랫폼 확인

```bash
docker buildx imagetools inspect <account>.dkr.ecr.ap-northeast-2.amazonaws.com/wearhouse/auth:<tag>
```

확인 포인트:

- `Platforms:`에 `linux/amd64`가 포함되어야 합니다.

## 5. 즉시 복구 방법 (서비스 단건)

`auth` 기준 예시입니다.

```bash
# 1) amd64로 재빌드/푸시
docker buildx build \
  --platform linux/amd64 \
  -f docker/java-service.Dockerfile \
  --build-arg MODULE_NAME=auth \
  -t <account>.dkr.ecr.ap-northeast-2.amazonaws.com/wearhouse/auth:<new-tag> \
  --push \
  .

# 2) deployment 이미지 교체
kubectl -n wearhouse set image deployment/auth-service \
  auth-service=<account>.dkr.ecr.ap-northeast-2.amazonaws.com/wearhouse/auth:<new-tag>

# 3) 롤아웃 확인
kubectl -n wearhouse rollout status deployment/auth-service --timeout=10m
kubectl -n wearhouse get pods -l app=auth-service
```

## 6. 현재 프로젝트에서 특히 주의할 점

- 배포 스크립트 기본값이 `DOCKER_PLATFORM=linux/amd64`입니다.
- 모듈별 순차 빌드/푸시이므로 중간 실패 시 일부 서비스만 태그가 바뀔 수 있습니다.
- 태그 혼재 상태가 되면 일부 서비스만 정상 기동하고 나머지는 `ImagePullBackOff`가 반복됩니다.

관련 파일:

- `scripts/eks/deploy.sh`
- `docker/java-service.Dockerfile`

## 7. 재발 방지 체크리스트

1. 태그를 immutable하게 운영합니다 (`latest` 지양, 배포마다 새 태그 사용).
2. 배포 전 `imagetools inspect`로 `linux/amd64` 포함 여부를 검증합니다.
3. 실패 서비스만 선별 재배포하되, 최종적으로 서비스 태그를 한 번 더 통일합니다.
4. CI에서 멀티아키텍처 빌드 정책을 강제합니다.

## 8. 코드 오류와 구분 기준

- 이미지 플랫폼 문제: `ImagePullBackOff`, 앱 로그 거의 없음, 컨테이너 시작 전 실패
- 코드 런타임 문제: `CrashLoopBackOff`, 애플리케이션 로그/스택트레이스 존재
