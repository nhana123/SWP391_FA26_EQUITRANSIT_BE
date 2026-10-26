[CmdletBinding()]
param([string]$SourceDirectory = '.local/subdivision-source')
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$source=Join-Path $root $SourceDirectory
$states=Get-Content -Raw (Join-Path $source 'states.json') | ConvertFrom-Json
$th=Get-Content -Raw (Join-Path $source 'th-adm1.json') | ConvertFrom-Json
function Normalize-RegionName($value) {
    ([regex]::Replace($value.Normalize([Text.NormalizationForm]::FormD),'\p{M}','').ToLowerInvariant() -replace '^changwat\s+','' -replace '\s+province$','' -replace '[^a-z0-9]','')
}
$cn=@{AH='An Huy';BJ='Bắc Kinh';CQ='Trùng Khánh';FJ='Phúc Kiến';GS='Cam Túc';GD='Quảng Đông';GX='Quảng Tây';GZ='Quý Châu';HI='Hải Nam';HE='Hà Bắc';HL='Hắc Long Giang';HA='Hà Nam';HK='Hồng Kông';HB='Hồ Bắc';HN='Hồ Nam';NM='Nội Mông';JS='Giang Tô';JX='Giang Tây';JL='Cát Lâm';LN='Liêu Ninh';MO='Ma Cao';NX='Ninh Hạ';QH='Thanh Hải';SN='Thiểm Tây';SD='Sơn Đông';SH='Thượng Hải';SX='Sơn Tây';SC='Tứ Xuyên';TW='Đài Loan';TJ='Thiên Tân';XJ='Tân Cương';XZ='Tây Tạng';YN='Vân Nam';ZJ='Chiết Giang'}
$out=@()
foreach($s in $states) {
    if($s.country_code -eq 'TH' -and $s.state_code -eq 'S') {continue}
    if($s.country_code -eq 'US' -and $s.type -notin @('state','district')) {continue}
    if($null -eq $s.latitude -or $null -eq $s.longitude -or $s.latitude -eq '' -or $s.longitude -eq '') {continue}
    $lat=[decimal]$s.latitude; $lon=[decimal]$s.longitude
    if([Math]::Abs($lat) -gt 90 -or [Math]::Abs($lon) -gt 180) {continue}
    $name=$s.name; $aliases=@(); $pointSource='csc'
    if($s.country_code -eq 'CN') {
        $aliases+=@($cn[$s.state_code])
        if($s.state_code -eq 'SD') {$lat=36.33333;$lon=118.25;$pointSource='geonames:1796328'}
    }
    if($s.country_code -eq 'TH') {
        $key=Normalize-RegionName $name
        $r=@($th | Where-Object {(Normalize-RegionName $_.name) -eq $key})
        if($r.Count -ne 1) {throw "Missing ADM1: $name"}
        $lat=$r[0].latitude;$lon=$r[0].longitude;$pointSource='geonames:'+$r[0].id
    }
    if($s.country_code -eq 'VN') {
        if($s.state_code -eq 'SG') {$aliases+=@('TP. Hồ Chí Minh','Ho Chi Minh City','HCMC','Saigon')}
        if($s.state_code -eq 'HN') {$aliases+='Hanoi'}
        if($s.state_code -eq 'HP') {$aliases+='Haiphong'}
        if($s.state_code -eq '26') {$aliases+=@('Thừa Thiên Huế','Hue')}
    }
    if($s.country_code -eq 'LA' -and $s.state_code -eq 'VI') {$name='Vientiane Province'}
    if($s.country_code -eq 'LA' -and $s.state_code -eq 'VT') {$aliases+=@('Vientiane Capital','Viêng Chăn')}
    if($s.country_code -eq 'LA' -and $s.state_code -eq 'BL') {$aliases+='Bolikhamxai'}
    if($s.country_code -eq 'TH' -and $s.state_code -eq '20') {$aliases+='Chonburi'}
    if($s.country_code -eq 'TH' -and $s.state_code -eq '14') {$aliases+='Ayutthaya'}
    if($s.country_code -eq 'US' -and $s.state_code -eq 'DC') {$aliases+=@('Washington D.C.','Washington DC')}
    $out+=[ordered]@{id=[int]$s.id;countryCode=$s.country_code;name=$name;code=($s.country_code+'-'+$s.state_code);aliases=@($aliases|Where-Object {$_});latitude=$lat;longitude=$lon;source=$pointSource}
}
foreach($pair in @{VN=63;LA=18;CN=34;TH=77;US=51}.GetEnumerator()) {
    if(@($out|Where-Object countryCode -eq $pair.Key).Count -ne $pair.Value) {throw "Coverage differs: $($pair.Key)"}
}
$sha=(Get-Content (Join-Path $source 'commit.txt')).Trim()
$target=Join-Path $root 'src/main/resources/geo'
New-Item -ItemType Directory -Force $target | Out-Null
[ordered]@{schemaVersion=1;edition='csc-2024-vn63-legacy';sourceCommit=$sha;sourceUrl="https://github.com/dr5hn/countries-states-cities-database/tree/$sha";license='ODbL-1.0; GeoNames corrections CC-BY-4.0';coordinateMeaning='Representative administrative-region points, not certified capital offices';subdivisions=$out;lookupPoints=@(@{id=1804651;countryCode='CN';name='Kunming';code='CN-KUNMING';aliases=@('Côn Minh','昆明');latitude=25.03889;longitude=102.71833;source='geonames:1804651'})} | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $target 'country-subdivisions.json') -Encoding utf8
Copy-Item (Join-Path $source 'LICENSE') (Join-Path $target 'LICENSE-ODbL.txt')
$out | ForEach-Object { [pscustomobject]$_ } | Where-Object countryCode -in @('VN','LA','CN','TH','US') | Group-Object countryCode | Select-Object Name,Count | ConvertTo-Json
Write-Output ('Total valid regions: '+$out.Count)

