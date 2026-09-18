# EarthShape × TerraFirmaCraft 호환성 검사

검사일: 2026-09-18. 판정: **현재 배포물은 서버 기동 실패. TFC 전체 월드젠 호환도 미구현.**

## 대상과 방법

- 현재 루트 프로젝트 EarthShape 1.1.4-A / Minecraft 1.21.1 / NeoForge 21.1.235.
- 사용자 지정 `E:/neoforge-launcher/mods/TerraFirmaCraft-NeoForge-1.21.1-4.2.7.jar`를 직접 검사.
- TFC SHA256: `86852D751A786ED4BD0B6F947667268AEB91E98DDDCA82A1C0680056366135CE`.
- EarthShape SHA256: `A66AA161219BD3D352104864157519592091CDE2F80B970D3D39891E80EC212F`.
- 소스·리소스·메타데이터 대조, TFC 113개 관련 클래스 및 별도 carver/placement/structure 클래스 바이트코드 검사, 오프라인 빌드, 격리된 전용 서버 실행 2회.
- Java 21.0.11, Patchouli 1.21.1-93-NEOFORGE, 두 번째 실행은 Chunky 1.4.23 추가.
- 시험 폴더: `build/tfc-compat-audit/server`. localhost:25589, seed 182026, 새 `tfc:overworld` 프리셋. 기존 서버 설정·모드·월드는 변경하지 않음.
- 프로젝트 문서의 기존 호환성 주장은 검증 대상 자료로만 취급함.

## 실제 재현한 차단 오류

### 1. [P1] ChunkMap 생성자 Mixin 때문에 새 월드 기동 실패

`src/main/java/io/github/earthshape/mixin/C2meOpenClFallbackMixin.java:28`은 `<init>`의 `HEAD`에 인스턴스 핸들러를 주입한다. 사용 중인 Mixin은 super 생성자 호출 이전 핸들러가 static이어야 한다며 거부한다.

두 번째 실행은 TFC 바이옴 125개를 인덱싱하고 `Preparing level "audit-world"`까지 진행한 후 다음 오류로 종료했다.

```text
InvalidInjectionException: @At("HEAD") selector @Inject handler before super() invocation must be static
Mixin: earthshape.mixins.json:C2meOpenClFallbackMixin
Target: net.minecraft.server.level.ChunkMap
```

C2ME를 설치하지 않은 시험에서도 발생했다. 본문의 ClassNotFoundException 처리는 Mixin 적용 단계의 오류를 막지 못한다. TFC 고유 충돌로 단정할 수 없는 EarthShape 기동 결함이다.

근거: `build/tfc-compat-audit/server/logs/latest.log:78`, `build/tfc-compat-audit/server/crash-reports/crash-2026-09-18_09.48.35-server.txt`.

우선 조치: 생성자 주입을 유효한 정적 핸들러 또는 적절한 생성자 주입 지점으로 변경하고, 의도한 C2ME 초기화 순서까지 재검증해야 한다.

### 2. [P1] 선택 의존성 Chunky가 없으면 EarthShape 로딩 실패

첫 실행(EarthShape + TFC + Patchouli)은 다음 오류로 종료했다.

```text
EarthShape (earthshape) has failed to load correctly
java.lang.NoClassDefFoundError: org/popcraft/chunky/shape/Shape
```

`EarthShape.java:25`에서 `ChunkyIntegration.registerIfPresent()`를 무조건 호출한다. 해당 클래스 내부의 설치 여부 검사에 도달하기 전에 Chunky 타입 연결이 발생하는 실행 경로가 있다. `compileOnly` 의존성 선언과 실제 동작이 일치하지 않는다.

근거: `build/tfc-compat-audit/no-chunky.log`, `src/main/java/io/github/earthshape/compat/ChunkyIntegration.java:17`.

사용자 서버에는 Chunky가 있으므로 그 구성에서는 첫 오류를 피하지만, 두 번째 실행에서 위 Mixin 오류가 발생했다. Chunky 클래스를 참조하는 구현의 로딩 자체를 외부 설치 여부 검사로 격리할 필요가 있다.

## 소스 및 JAR로 확인한 기능 호환성

| 영역 | 판정 | 확인 내용 |
|---|---|---|
| Minecraft / NeoForge / Patchouli 버전 | 조건 충족 | TFC는 MC 1.21.1, NeoForge ≥21.1.234, Patchouli ≥1.21.1-92 요구. 시험 구성은 충족 |
| 프로젝트 빌드 | 통과 | `gradlew.bat build --offline --no-daemon`: BUILD SUCCESSFUL. 자동 테스트는 NO-SOURCE |
| TFC 바이옴 등록 | 통과 | 서버 로그에서 125개 인덱싱 확인. 실제 청크 배치는 기동 오류로 미확인 |
| TFC 원래 청크 생성기 | 대체됨 | EarthShape의 `data/tfc/worldgen/world_preset/overworld.json:6`은 `tfc:overworld` 생성기를 `minecraft:noise`로 변경 |
| 지층·암석·토양 | 전체 호환 아님 | TFC 생성기·surface pipeline이 실행되지 않음. 현재 TfcSurfaceRules는 지정된 사막 바이옴 12종의 표면에 노란 모래를 추가하는 수준 |
| TFC 광맥·생존 자원 | 중대한 기능 불일치 | TFC 원석을 치환하는 광맥은 원석이 없는 바닐라 기반 지층에서 정상 자원 분포를 만들 수 없음 |
| 숲·낙석·온천·동굴 장식 | 명시적으로 생략 | TfcGeneratorFeatureGuardMixin이 비-TFC 생성기에서 15개 feature의 place를 false로 종료 |
| 온도·강수·지하수·숲 데이터 | 연결 없음 | EarthShape 지도 레이어를 TFC ChunkData로 채우는 구현이 없음. TFC 클래스에는 미생성 데이터의 기본값이 존재 |
| 구조물 | 조건부 누락 가능 | TFC ClimateStructurePlacement는 생성기 확장이 없으면 false 반환. 해당 placement를 사용하는 구조물/데이터팩은 생성되지 않음. 기본 구조물 전체가 사라진다는 뜻은 아님 |
| 바이옴 선택 범위 | 프리셋에 한정되지 않음 | TerrainBiomeMixin:119는 TFC 바이옴 존재 여부로 분기. 일반 오버월드 MultiNoise 소스에도 적용 가능 |
| TFC 전용 바이옴 보장 | 코드상 예외 있음 | selectTfc의 후보가 없으면 null, 호출자는 바닐라/기타 선택으로 계속 진행. 모든 조건에서 TFC-only라는 문서 주장은 보장되지 않음 |
| TFC 동굴 바이옴 | 별도 선택 경로 없음 | selectTfcMapBiome는 RIVER/OCEAN/COAST/LAND만 선택. TfcBiomeLayers의 CAVE 분류를 이 경로에서 사용하지 않음 |
| 기존 TFC 월드 | 호환 승인 불가 | 프리셋 교체는 저장된 생성기/기존 청크의 변환 도구가 아님. TFC의 원래 RegionBiomeSource에 EarthShape의 MultiNoise 후킹이 그대로 적용되지 않음 |

광맥의 구체적 근거: TFC JAR의 `data/tfc/worldgen/configured_feature/vein/surface_native_copper.json`은 rhyolite/basalt/andesite/dacite의 `tfc:rock/raw/*`만 치환한다. 생성 함수가 호출되는 것만으로 광맥 호환이 성립하지 않는다. 실제 생성량은 측정하지 못했다.

생략되는 feature: BouldersFeature, DynamicDensityRandomPatchFeature, ErosionFeature, FissureFeature, HotSpringFeature, LooseRockFeature, NoisyMultipleFeature, SeaStacksFeature, SoilForestAreaFeature, SpringFeature, TidePoolFeature, CaveColumnFeature, CaveSpikesFeature, IceCaveFeature, ForestFeature.

ChunkData 바이트코드에서는 미생성 온도층의 10°C 기본값, 기저 지하수 0, ForestType.GRASSLAND 초기값을 확인했다. 이는 모든 게임 내 온도가 반드시 10°C라는 뜻은 아니다. 소비자가 사용하는 ClimateModel과 ChunkData 경로에 따라 다르며, EarthShape 지도 온도와 TFC 생태·작물 조건이 일치한다는 근거는 없다.

## 검증 범위의 한계

- 두 번째 실행이 청크 생성 전 실패했으므로 지형·해안·강·해저·광물 분포, 장시간 Chunky 생성, 저장/재접속, 실제 클라이언트 플레이를 통과로 판정하지 않았다.
- 런처 mods 폴더에는 Chunky, Patchouli, Spore, squaremap, TFC, tfc_spore_compat가 있었고 EarthShape는 없었다. 과거 런처 로그는 현재 EarthShape+TFC의 성공 증거가 아니다.
- Spore/squaremap/tfc_spore_compat까지 넣은 전체 모드팩 조합은 미실행. 최소 조합부터 치명적 오류가 있어 전체 모드팩 호환을 승인할 수 없다.
- Forge 1.20.1 및 Fabric 26.2 하위 프로젝트는 지정된 NeoForge 1.21.1 JAR의 실행 대상이 아니다.
- TFC 로그의 일부 ClientLevel 로딩 ERROR는 바로 뒤에서 TFC가 초기 리소스 리로드에서 허용되는 상황이라고 설명한다. 이것을 이번 기동 실패 원인으로 오인하지 않았다.

## 다음 작업 순서

1. 두 기동 결함을 수정하고 동일한 격리 서버에서 새 월드 생성·저장·재시작을 검증.
2. TFC ChunkData, rock strata, surface, forest 및 광맥을 보존하는 생성기 브리지를 설계. 현재 feature 생략 방식으로 전체 호환을 달성할 수는 없음.
3. 바이옴 선택의 프리셋 범위, 빈 후보 처리, 지하 바이옴 경로를 명시적으로 정리.
4. 여러 지도 지역에서 자원·기후·하천·해양·구조물과 실제 생존 진행을 검증하고 전체 모드팩으로 확대.

이번 요청은 검사이므로 소스 수정이나 서버 배포는 하지 않았다. 보고서와 시험 산출물만 생성했다.
