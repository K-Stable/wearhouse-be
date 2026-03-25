# 이미지 아키텍처 불일치 트러블슈팅 (`no match for platform in manifest`)

## 1. 증상
- Pod 상태가 `ErrImagePull` → `ImagePullBackOff`로 반복된다.
- 이벤트에 아래 메시지가 보인다.
  - `no match for platform in manifest`
  - `Back-off pulling image ...`

## 2. 의미
- 쿠버네티스 노드(예: `linux/amd64`)가 pull 가능한 플랫폼 매니페스트를 이미지 태그에서 찾지 못한 상태다.
- 이 문제는 **앱 코드 실행 전 단계**에서 실패하므로, 애플리케이션 로그가 거의 없을 수 있다.

## 3. 왜 오래 걸리는가
- kubelet이 pull 실패 시 자동 재시도한다.
- 실패 횟수가 누적될수록 재시도 간격이 늘어나는 지수 백오프가 적용된다.
- 따라서 이벤트에 `Back-off pulling image`가 수십/수백 회 누적될 수 있다.

## 4. 대표 원인
- ARM 로컬(M1/M2)에서 빌드되어 `linux/arm64`만 푸시됨
- EKS 노드는 `linux/amd64`인데 이미지가 멀티아치가 아님
- 같은 태그를 다른 아키텍처로 덮어써 매니페스트가 꼬임
- buildx 사용 시 `--push`/`--load` 조합 오류로 원하는 플랫폼이 누락됨

## 5. 즉시 점검 순서
1. Pod 이벤트 확인
```bash
kubectl -n wearhouse describe pod <pod-name>
```
2. 배포 이미지 태그 확인
```bash
kubectl -n wearhouse get deploy <deploy-name> -o yaml | grep image:
```
3. 푸시된 태그의 플랫폼 확인
```bash
docker buildx imagetools inspect <image:tag>
```
4. `Platform: linux/amd64` 존재 여부 확인

## 6. 표준 복구 절차
1. 이미지 재빌드 시 플랫폼 고정
```bash
docker build --platform linux/amd64 -t <image:tag> <context>
docker push <image:tag>
```
2. Deployment 이미지 교체
```bash
kubectl -n wearhouse set image deployment/<deploy> <container>=<image:tag>
kubectl -n wearhouse rollout status deployment/<deploy> --timeout=10m
```
3. 이벤트 재확인 (`Pulled`, `Started` 확인)

## 7. 재발 방지 규칙
- EKS 배포용 빌드는 기본값을 항상 `linux/amd64`로 고정한다.
- 서비스별로 태그를 새로 발급해 덮어쓰기 충돌을 피한다.
- 배포 전 `imagetools inspect`로 플랫폼 검증을 자동화한다.
