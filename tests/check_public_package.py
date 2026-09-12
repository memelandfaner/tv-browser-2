"""Check distributable assets, never developer files or browsing restrictions."""
from pathlib import Path
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
source = ROOT / 'src/main/kotlin/si/safeer/tv'
for asset in (ROOT / 'assets').rglob('*'):
    if asset.is_file():
        assert not any(part in asset.name.lower() for part in ['auth', '.local.', '.jks', '.keystore']), asset.name
for name in ['HomeTilesStore.kt', 'PortalManager.kt', 'BrowserRepository.kt']:
    text = (source / name).read_text()
    for forbidden in ['hydrahd.ws', 'streamnexus+hd', '192.168.']:
        assert forbidden not in text, (name, forbidden)
for file in sys.argv[1:]:
    with zipfile.ZipFile(file) as archive:
        names = archive.namelist()
        assets = [name for name in names if '/assets/' in '/' + name]
        for name in assets:
            assert not any(part in name.lower() for part in ['auth', '.local.', '.jks', '.keystore']), name
        homes = [name for name in assets if name.endswith('/brave_home.html')]
        assert len(homes) == 1, file
        home = archive.read(homes[0]).decode()
        assert 'hydrahd.ws' not in home and '192.168.' not in home, file
        assert not any(name.endswith(('.so', '.db', '.sqlite', '.jks', '.keystore')) for name in names), file
        print('PASS public assets:', file)
print('PASS public defaults; user navigation remains unrestricted.')
