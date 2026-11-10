# 산업 설비형 시안

기존 모델을 덮어쓰지 않고 만든 AE2 스타일의 조립 라인 4종입니다. 흰색 외장·흑연 프레임·작은 보라색 상태등을 사용하며, 인터페이스는 AE2 원본 이미지를 유지합니다. `../assets/`의 기존 텍스처를 공유하므로 이 폴더만 따로 옮기지 마세요.

| 파일 | 크기(X×Y×Z) | 설비 구성 |
| --- | --- | --- |
| `industrial_assembler_ammo.bbmodel` | 32×24×16 | 프레스 베이, 가이드 축, 공급 모듈, 검사 아치 |
| `industrial_assembler_attachment.bbmodel` | 32×24×16 | 소형 조립 팔 두 대, 정밀 헤드, 광학 검사부 |
| `industrial_assembler_gun.bbmodel` | 32×28×32 | 중앙 조립 장치, 두 공구 헤드, 측면 부품 벨트와 검사 구역 |
| `industrial_assembler_universal.bbmodel` | 32×28×32 | 개방형 자동화 셀, XY 브리지, 교체 공구 랙 |

모두 Free Model이며 `animation.crafting`을 재생하면 20초 주기로 동작합니다. 트레이와 작업물이 인터페이스에서 함께 등장하고, 6~12초에 제작을 거친 뒤 함께 출구로 들어가 사라집니다. 기존 컨베이어·트레이의 이동 경로는 재사용하고 상부 설비와 제작 동작은 새로 구성했습니다.

총기·부착물·범용 어셈블러의 하부는 장식 패널·표시등·환기구를 제거하고, 틈 없이 연결된 회색 외장과 짙은 프레임으로 통일했습니다. 상부 설비와 애니메이션은 유지했습니다.

Blockbench에서 직접 캡처한 미리보기는 `preview_ammo.png`, `preview_attachment.png`, `preview_gun.png`, `preview_universal.png`입니다. 4종 모두 저장 파일 검사를 통과했으며, 20초 주기 401개 시점의 새 설비/작업물 경계 상자 검사에서 겹침을 발견하지 않았습니다. 기존 모델 8개는 SHA-256 비교로 변경되지 않았음을 확인했습니다.

게임용으로 단순화한 외형 시안이며 실제 제조 설계가 아닙니다. 작업물은 기존 미리보기 모형이고, 현재 제작 결과 ItemStack을 동적으로 표시하는 기능은 아직 연결하지 않았습니다. Minecraft 게임 리소스·Java 렌더러·배포 파일은 수정하지 않았습니다.

저장소 루트에서:

```sh
node design/assemblers/blockbench/industrial/generate-industrial.mjs
node design/assemblers/blockbench/industrial/generate-industrial.mjs --check
node design/assemblers/blockbench/industrial/check-industrial.mjs
```

재생성은 이 시안 폴더의 `.bbmodel` 파일을 덮어씁니다. Blockbench에서 직접 수정한 시안은 먼저 별도 이름으로 저장하세요. 상위 폴더의 기존 모델 8개는 변경하지 않습니다.
