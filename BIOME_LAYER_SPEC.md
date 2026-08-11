# EarthShape 1.1.3-A 레이어 조건별 생성 가능 바이옴 명세

## 1. 적용 범위

이 문서는 현재 소스의 `TerrainBiomeMixin`, `ClimateLayers`, `RiversMask`, `AdditionalBiomeRegistry`를 기준으로 오버월드의 최종 바이옴 후보를 정리한 것이다.

- 지표 판정 기준 높이: `Y >= 48`
- `Y < 48`: 바닐라 동굴 바이옴을 유지한다. 다만 `terrain.bmp` 피복 영역에서 동굴 입구가 지상 설원 바이옴으로 잘못 판정된 경우에만 비설원 바이옴으로 교정한다.
- 실제 고도는 바이옴 선택과 별도로 바닐라 밀도 노이즈 및 EarthShape 지형 밀도 보정에서 결정한다. 이 문서는 바이옴 선택만 다룬다.
- TerraBlender·Biolith·Lithostitched·Climate Rivers 등 외부 선택기는 정상적으로 계산을 끝내지만 반환값은 폐기된다. `getNoiseBiome`의 모든 반환 경로에서 EarthShape가 마지막으로 다시 판정하며, 추가 모드 바이옴은 `AdditionalBiomeRegistry`의 레이어/태그 조건을 통과한 경우에만 배치된다.

## 2. 입력 레이어와 역할

| 입력 | 권한 | 용도 |
|---|---|---|
| `worldmap_river.png` 육지/해양 마스크 | 최우선 | 육지와 바다를 확정한다. 해양 마스크 위치에서는 `terrain.bmp`의 육지색보다 해양 판정이 우선한다. |
| `rivers.bmp` | 최우선 | 내륙 강의 위치, 폭, 하구 및 강줄기 단위 결빙 상태를 결정한다. |
| `terrain.bmp` | 지표 바이옴 계열 | 사막·습지·숲·정글·평야·구릉·산악 계열을 제한한다. |
| `trees.bmp` | 보조 | 명시된 숲의 열대/온대 성격을 보조하고, `PLAINS` 픽셀을 정글 또는 숲으로 승격할 수 있다. 명시적 사막·습지 등을 숲으로 바꾸지는 않는다. |
| `earth_temperature.png` | 온도/적설 | 육지의 툰드라·타이가·설원 허용 여부와 해양 온도대를 결정한다. 미피복 부분은 위도 기반 온도로 이어진다. |
| 바닐라 T/H/C/E/D/W 노이즈 | 계열 내부 선택 | 레이어가 허용한 후보군 안에서 가장 가까운 바닐라 기후 후보를 고른다. 구릉·산악은 바닐라 침식도와 기이함을 그대로 더 많이 보존한다. |

`terrain.bmp`, `trees.bmp`, 온도 레이어의 긴 픽셀 경계에는 기본 최대 24블록의 완만한 워프가 적용된다. 강과 해안 마스크는 위치 정확도를 위해 워프하지 않는다.

## 3. 최종 선택 우선순위

아래에서 먼저 일치한 규칙이 뒤 규칙보다 우선한다.

1. `rivers.bmp`의 유효한 내륙 강: `minecraft:river` 또는 강줄기 전체가 `minecraft:frozen_river`
2. 남쪽 끝 영구설원 육지: 지형 종류와 무관하게 `minecraft:snowy_plains`
3. 적격 해안 패치: 해변 계열 후보
4. 추가 모드 바이옴: 약 2/3의 1024블록 지역에서 해당 공통 태그 후보
5. 바닐라 후보: 레이어 계열로 필터링한 뒤 바닐라 다중 노이즈 최인접 후보
6. 해당 후보 태그가 비어 있는 비표준 데이터팩: 전체 기후 후보표로 돌아가지 않고 같은 terrain 계열의 명시적 바닐라 바이옴으로 폴백

## 4. 추가 모드 바이옴 조합 후보표

추가 바이옴은 단일 태그로 바로 선택하지 않는다. 아래 값 전체를 `LayerKey`로 묶고 동일한 조합에서 허용되는 후보 풀을 최초 1회 생성한 뒤 그 풀 안에서만 선택한다.

- `terrain.bmp`: terrain 종류
- `trees.bmp`: `NONE`, `TEMPERATE`, `TROPICAL`
- `earth_temperature.png`: 9단계 온도 구간
- 수문 상태: `LAND`, `OCEAN`, `COAST`, `RIVER`, `CAVE`
- 적설 허용 여부
- 완전한 산봉우리 영역 여부
- 악지/메사 영역 여부

| 수문/terrain 조합 | 필요한 후보 태그 | 동시에 있으면 배제되는 태그/조건 |
|---|---|---|
| `CAVE` | `IS_CAVE` 또는 `IS_UNDERGROUND` | 해양·해변·강 태그 |
| `RIVER` | `IS_RIVER` | 해양·해변·동굴 태그; 결빙 강은 추가 후보를 사용하지 않음 |
| `OCEAN` | `IS_OCEAN` | 강·해변·동굴 태그 |
| `COAST` | `IS_BEACH` | 해양·강·동굴 태그 |
| `LAND + DESERT + mesa` | `IS_BADLANDS` | 숲·타이가·평야·사바나·정글·늪·산악 태그, 저온 구간 |
| `LAND + DESERT` | `IS_DESERT` | 다른 모든 지상 주계열 태그, 저온 구간 |
| `LAND + WETLAND` | `IS_SWAMP` | 다른 모든 지상 주계열 태그 |
| `LAND + JUNGLE` | `IS_JUNGLE` | 다른 모든 지상 주계열 태그, 저온 구간 |
| `LAND + FOREST + TROPICAL + 고온` | `IS_JUNGLE` | 다른 모든 지상 주계열 태그 |
| `LAND + FOREST + 저온` | `IS_TAIGA` (`IS_FOREST` 중복만 허용) | 다른 모든 지상 주계열 태그 |
| `LAND + FOREST + 온난` | `IS_FOREST` | 타이가 및 다른 모든 지상 주계열 태그 |
| `LAND + HILLS` | `IS_MOUNTAIN_SLOPE` (`IS_MOUNTAIN` 중복 허용) | 봉우리 및 다른 지상 주계열 태그 |
| `LAND + MOUNTAIN` | `IS_MOUNTAIN_PEAK` (`IS_MOUNTAIN` 중복 허용) | 산비탈 및 다른 지상 주계열 태그 |
| `LAND + PLAINS/CITY/SURROUNDING + 고온` | `IS_SAVANNA` | 다른 모든 지상 주계열 태그 |
| `LAND + PLAINS/CITY/SURROUNDING + 비고온` | `IS_PLAINS` | 다른 모든 지상 주계열 태그 |

모든 조합에 공통으로 적설이 허용되지 않으면 `IS_SNOWY`를 배제한다. 저온 구간에서는 `IS_HOT`, 고온 구간에서는 `IS_COLD`와 `IS_SNOWY`를 배제한다. 지상 후보에는 해양·해변·강·동굴·지하 태그가 하나라도 섞이면 배제한다.

추가 모드 바이옴의 2/3 배치는 블록마다 무작위로 바뀌지 않는다. `(X >> 10, Z >> 10)` 기반의 결정적 지역 해시를 사용하므로 같은 월드 좌표에서는 항상 같은 후보가 선택된다.

## 5. `terrain.bmp` 색상 분류

| 유효 분류 | 대표 색상 |
|---|---|
| `WATER` | `#081F82` |
| `DESERT` | `#CEA963`, `#FCFF00` |
| `WETLAND` | `#4B93AE` |
| `FOREST` | `#005606`, `#06C80B`, `#3A8352` |
| `JUNGLE` | `#005252` |
| `PLAINS` | `#567C1B`, `#84FF00` |
| `HILLS` | `#704A1F`, `#728969`, `#86541E` |
| `MOUNTAIN` | `#493B0F`(및 `#493B0D`), `#5C534C`, `#AE00FF`, `#FFFFFF` |
| `SURROUNDING` | `#F0FF00` |
| `CITY` | `#FF0018`, `#FF007F`, `#FF00F0` |

알 수 없는 색은 가장 가까운 팔레트 색으로 분류된다. `CITY`와 `SURROUNDING`은 그대로 독립 바이옴이 되지 않고 주변의 유효 육지 계열로 병합된다. 너무 작은 산악 조각도 주변 육지 계열로 병합된다. 레이어 범위 밖의 육지는 기본적으로 `PLAINS`로 취급하되, 온도 레이어가 존재하는 범위에서는 온도와 해안 대륙성만 바닐라 후보에 유도한다.

## 6. 레이어별 생성 가능 바이옴

### 5.1 WATER

조건: 육지/해양 마스크가 해양이거나 유효 지형 분류가 `WATER`인 위치.

| 온도 `T` | 연안/좁은 해역 | 열린 바다 |
|---|---|---|
| `T > 0.65` | `minecraft:warm_ocean` | `minecraft:warm_ocean` |
| `0.15 < T <= 0.65` | `minecraft:lukewarm_ocean` | `minecraft:deep_lukewarm_ocean` |
| `-0.15 < T <= 0.15` | `minecraft:ocean` | `minecraft:deep_ocean` |
| `-0.5 < T <= -0.15` | `minecraft:cold_ocean` | `minecraft:deep_cold_ocean` |
| `T <= -0.5` | `minecraft:frozen_ocean` | `minecraft:deep_frozen_ocean` |

- 열린 바다는 중심에서 동서남북 180블록 지점이 모두 해양값 `< 0.25`일 때다.
- 추가 바이옴 후보 태그: `is_ocean`.
- 현재 BYG 후보 중 `biomeswevegone:dead_sea`는 해양 후보로 사용할 수 있다.
- `biomeswevegone:lush_stacks`는 해양 태그가 있어도 해수면 위에 큰 암석 지형을 직접 생성하므로 제외한다. 원본 육지/해양 마스크 밖에 반복적인 막대 섬이 생기는 것을 방지하기 위한 예외다.
- 추운 바다는 `is_snowy` 후보가 있으면 이를 우선하지만, 현재 BYG의 두 해양 후보는 snowy 태그가 아니다.
- 하구는 강이 바다를 찌르는 대신 해양 후보군으로 처리된다.

### 5.2 DESERT

| 구분 | 바닐라 생성 가능 바이옴 | 추가 바이옴 태그 |
|---|---|---|
| 일반 사막 | `minecraft:desert` | `is_desert` |
| 메사 영역 | `minecraft:badlands`, `minecraft:wooded_badlands`, `minecraft:eroded_badlands` | `is_badlands` |

- 기후 유도값은 최소 온도 `0.78`, 습도 `-0.82`, 대륙성 `0.12`, 침식도 `0.34`다.
- 일반 사막의 BYG 후보: `atacama_outback`, `mojave_desert`, `windswept_desert`.
- 메사 BYG 후보: `red_rock_peaks`, `red_rock_valley`, `rugged_badlands`, `sierra_badlands`.
- 사막 계열에서는 설원 추가 바이옴을 허용하지 않는다.
- `terrain.bmp`가 `DESERT`이면 온도가 높더라도 정글·사바나·습지·평야 태그와 겹치는 후보는 제외한다. 사막/악지 후보가 없는 데이터팩에서도 다른 고온 계열로 폴백하지 않고 `minecraft:desert` 또는 바닐라 악지 계열을 사용한다.

### 5.3 WETLAND

| 조건 | 바닐라 생성 가능 바이옴 | 추가 바이옴 태그 |
|---|---|---|
| `T <= 0.3` | `minecraft:swamp` | `is_swamp` |
| `T > 0.3` | `minecraft:mangrove_swamp` | `is_swamp` |

- 기후 유도값은 습도 `0.82`, 침식도 `0.72`다.
- BYG 후보: `bayou`, `cypress_swamplands`, `cypress_wetlands`, `pale_bog`, `white_mangrove_marshes`.
- 늪 바이옴 자체가 생성하는 지표수는 별도 수면 억제 대상에서 제외된다.

### 5.4 FOREST

| 온도/눈 조건 | 바닐라 생성 가능 바이옴 | 추가 바이옴 태그 |
|---|---|---|
| 설원 허용, `T < -0.55` | `minecraft:snowy_taiga` | `is_taiga` 중 snowy 우선 |
| 설원 허용, `-0.55 <= T < -0.25` | `minecraft:taiga`, `minecraft:old_growth_pine_taiga`, `minecraft:old_growth_spruce_taiga` | `is_taiga` |
| 툰드라 기준 이하이나 설원 기준 초과 | 비설원 타이가 후보 | `is_taiga` 비설원 |
| 그 외 | `minecraft:forest`, `flower_forest`, `birch_forest`, `dark_forest`, `old_growth_birch_forest` | `is_forest` 비타이가·비설원 |

- `T < -0.2`이면 추가 후보는 `is_taiga`, 그 외에는 `is_forest`에서 고른다.
- 온대 숲이 산악에서 80블록 이내이고 지역 해시가 3분할 중 하나이면 넓은 산록 초지를 만들기 위해 `PLAINS` 후보로 전환될 수 있다.
- BYG `is_forest` 후보: `aspen_boreal`, `black_forest`, `canadian_shield`, `cika_woods`, `coniferous_forest`, `dacite_ridges`, `ebony_woods`, `enchanted_tangle`, `eroded_borealis`, `forgotten_forest`, `frosted_coniferous_forest`, `howling_peaks`, `orchard`, `overgrowth_woodlands`, `redwood_thicket`, `sakura_grove`, `skyris_vale`, `weeping_witch_forest`, `zelkova_forest`.
- BYG `is_taiga` 후보: `frosted_taiga`, `maple_taiga`.
- 현재 BYG snowy 후보는 `eroded_borealis`, `howling_peaks`이며, 요청 태그와 snowy 태그를 동시에 만족할 때만 설원 우선 후보가 된다.

### 5.5 JUNGLE

바닐라 후보는 `minecraft:jungle`, `minecraft:sparse_jungle`, `minecraft:bamboo_jungle`이다.

- 기후 유도값은 최소 온도 `0.72`, 습도 `0.92`, 침식도 `0.46`이다.
- 추가 후보 태그: `is_jungle`.
- BYG 후보: `crag_gardens`, `fragment_jungle`, `jacaranda_jungle`, `tropical_rainforest`.
- 정글에서는 설원 추가 바이옴을 허용하지 않는다.

### 5.6 PLAINS

`CITY`와 `SURROUNDING`이 주변 계열로 병합되지 못한 경우도 이 계열과 같은 후보 규칙을 사용한다. 또한 `terrain.bmp`의 `PLAINS` 위치는 `trees.bmp`가 열대이면 `JUNGLE`, 온대이면 `FOREST`로 승격될 수 있다.

| 조건 | 바닐라 생성 가능 바이옴 | 추가 바이옴 태그 |
|---|---|---|
| 설원 허용, `T < -0.55` | `minecraft:snowy_plains`, 드물게 `minecraft:ice_spikes` | `is_plains` 중 snowy 우선 |
| 설원 허용, `-0.55 <= T < -0.3` | `minecraft:snowy_plains` | `is_plains` 중 snowy 우선 |
| 비설원, `T > 0.45` | `minecraft:savanna`, 드물게 `minecraft:savanna_plateau` | `T > 0.4`이면 `is_savanna` |
| 그 외 | `minecraft:plains`, 드물게 `minecraft:sunflower_plains` | `is_plains` |

- BYG `is_plains` 후보: `allium_shrubland`, `amaranth_grassland`, `coconino_meadow`, `crimson_tundra`, `firecracker_chaparral`, `prairie`, `pumpkin_valley`, `rose_fields`, `temperate_grove`.
- BYG `is_savanna` 후보: `araucaria_savanna`, `baobab_savanna`, `ironwood_gour`.

### 5.7 HILLS

| 조건 | 바닐라 생성 가능 바이옴 | 추가 바이옴 태그 |
|---|---|---|
| 설원 허용, `T < -0.55` | `minecraft:snowy_slopes` | `is_mountain/slope` 중 snowy 우선 |
| 설원 허용, 그 외 | `minecraft:grove` | `is_mountain/slope` 중 snowy 우선 |
| 비설원, `T > 0.45` | `minecraft:windswept_savanna` | `is_mountain/slope` 비설원 |
| 비설원, 그 외 | `minecraft:windswept_hills`, 드물게 `minecraft:windswept_forest` | `is_mountain/slope` 비설원 |

- 바닐라 필터 경로에서는 비설원 `is_hill` 또는 `is_mountain/slope`, 설원에서는 `grove` 또는 `snowy_slopes`만 허용한다.
- BYG slope 후보: `canadian_shield`, `shattered_glacier`.
- snowy 필터 때문에 `shattered_glacier`는 해당 JAR에서 `is_snowy`도 붙어 있어야 추운 구릉의 우선 후보가 된다. 현재 BYG snowy 목록에는 포함되지 않아 일반 slope 풀에서 선택될 수 있다.

### 5.8 MOUNTAIN

| 조건 | 바닐라 생성 가능 바이옴 | 추가 바이옴 태그 |
|---|---|---|
| 일반 산악 | `minecraft:stony_peaks` 및 허용된 산비탈/구릉 후보 | `is_mountain/peak` 비설원 |
| 대형 산악계이며 흰색 초고산 또는 극지 온도대 | `minecraft:frozen_peaks` 및 산악 봉우리 후보 | `is_mountain/peak` 중 snowy 우선 |
| 온대 나무 피복, `-0.25 < T < 0.35`, 24개 지역 중 하나 | 예외적으로 `minecraft:cherry_grove` 허용 | 동일 조건의 일반 산악 후보 |

- 대형 산악계 조건은 연결 산악 영역의 높이 배율 `>= 0.55`다.
- 흰색 `#FFFFFF`은 유일한 초고산 색이다.
- BYG peak 후보: `howling_peaks`, `red_rock_peaks`.
- `howling_peaks`는 snowy 태그, `red_rock_peaks`는 비설원 후보다.
- 작고 고립된 산악 조각은 산악 후보를 받지 않고 주변 육지 계열로 병합된다.

### 5.9 해안 패치

해변은 해안선 전체에 강제 도색하지 않는다. 다음 조건을 모두 만족하는 넓은 결정적 패치에서만 생성된다.

- 지표 판정
- 강이나 하구가 아님
- `DESERT`, `HILLS`, `MOUNTAIN` 중 하나
- 중심은 육지이고 동서남북 28블록 중 하나 이상이 해양
- 5개 지역 패턴 중 하나

가능 후보:

- 사막 해안: `minecraft:beach` 또는 추울 때 `minecraft:snowy_beach`, 추가 `is_beach`
- 구릉/산악 해안: `minecraft:stony_shore` 또는 `minecraft:snowy_beach`, 추가 `is_beach`
- BYG beach 후보: `basalt_barrera`, `dacite_shore`, `rainbow_beach`

해변 바이옴 판정은 해안 고도나 수심을 직접 결정하지 않는다.

### 5.10 내륙 강과 하구

- 유효 내륙 강은 다른 모든 지형·추가 바이옴 후보보다 먼저 `minecraft:river`로 확정한다.
- 연결된 강줄기의 결빙 상태가 참이면 강 전체를 `minecraft:frozen_river`로 확정한다. 국소 온도 차이로 같은 강줄기가 중간만 얼지 않는다.
- 강에서 `deep_frozen_ocean`을 비롯한 해양 바이옴은 선택할 수 없다.
- 하구 또는 해양 마스크 위치는 해양 후보만 허용하여 강 바이옴이 바다 안으로 뻗지 않게 한다.
- 강변의 중립 `PLAINS` 픽셀은 실제 수로 밖에서 주변 8방향의 명확한 다수 계열이 확인될 때만 그 계열로 복원한다.

## 7. 온도·적설 공통 조건

기본 설정값 기준이다.

| 조건 | 기본값 | 효과 |
|---|---:|---|
| 툰드라/타이가 허용 | `T <= -0.25` | `FOREST`에서 비설원 타이가 계열 허용 |
| 저지대 눈 허용 | `T <= -0.625` | 지표 설원 바이옴 허용 |
| 고산 설선 | `Y >= 160` | `HILLS`와 `MOUNTAIN`에 한해서 온도와 무관하게 눈 허용 |
| 남쪽 영구설원 | 하단 경계/남극 마스크 | 모든 육지 계열을 나무 없는 `snowy_plains`로 고정 |

고산 설선은 일반 평야·숲 및 동굴 입구에는 적용하지 않는다. 따라서 동굴의 높은 quart 샘플 때문에 일반 지표가 `snowy_taiga`로 바뀌는 경로는 차단되어 있다.

## 8. 추가 바이옴 등록 및 실제 선택 조건

EarthShape는 서버 시작 시 다음 조건을 만족하는 바이옴을 추가 후보로 등록한다.

1. 네임스페이스가 `minecraft`가 아님
2. `is_overworld` 태그 보유
3. 아래 중 적어도 하나의 분류 태그 보유: `is_desert`, `is_badlands`, `is_swamp`, `is_jungle`, `is_forest`, `is_taiga`, `is_mountain`, `is_mountain/peak`, `is_mountain/slope`, `is_plains`, `is_savanna`, `is_ocean`, `is_beach`

설원 선택 여부는 추가로 `is_snowy` 태그를 사용한다. 등록된 바이옴은 오버월드 `BiomeSource.possibleBiomes`에도 병합되므로 해당 바이옴의 세대 기능과 표면 규칙이 실행될 수 있다.

현재 검사 대상인 Oh The Biomes We've Gone 2.6.0은 오버월드 바이옴 55개 전부가 위 분류 태그 중 하나 이상에 포함된다. 따라서 55개 모두 레이어 조건이 맞는 지역에서 선택 가능하다. 다만 한 바이옴이 여러 태그에 겹치면 어느 레이어에서든 무조건 나오는 것이 아니라, 요청된 계열 태그와 적설 필터를 동시에 통과해야 한다.

## 9. 레이어 미피복 및 비활성화 동작

- 육지/해양 마스크상 바다는 언제나 `WATER`가 우선한다.
- 육지인데 `terrain.bmp` 범위 밖이면 지형 계열을 임의로 냉대나 빙하로 만들지 않고 기본 `PLAINS`로 처리한다.
- `terrain.bmp` 피복은 없지만 온도 맵 피복이 있으면 바닐라 습도·침식도·깊이·기이함을 유지하면서 온도와 해안 대륙성만 유도한다.
- `terrainBiomesEnabled=false`이면 이 문서의 지형 계열 필터를 적용하지 않고 바닐라 후보 선택으로 돌아간다.
- `riverBiomesEnabled=false`이면 `rivers.bmp` 강 바이옴 강제 규칙을 적용하지 않는다.
- `oceanTemperatureEnabled=false`이면 레이어 온도로 해양 온도대를 다시 고르지 않는다.
- 후보 모드가 설치되지 않았거나 해당 공통 태그 풀이 비어 있으면 바닐라 후보만 사용한다.

## 10. 구현상 주의점

- “생성 가능”은 해당 후보 풀에 들어갈 수 있다는 뜻이다. 실제 좌표의 최종 후보는 온도, 적설, 산악 연결 규모, 해안/하구 여부, 지역 해시 및 바닐라 기후 최인접 계산에 따라 달라진다.
- BYG 태그 파일이 바뀌는 버전에서는 같은 바이옴이라도 후보 계열이 달라질 수 있다.
- 추가 모드 바이옴을 태그 없이 `is_overworld`에만 등록하면 `possibleBiomes`에는 포함되지만 EarthShape 레이어 선택 후보에는 들어가지 않는다. 서버 로그의 `uncategorized` 값으로 이를 확인할 수 있다.
