const fs = require('fs');
const path = require('path');
const {pathToFileURL} = require('url');
const {chromium} = require('C:/Users/thang/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
(async()=>{
 const browser = await chromium.launch({headless:true,executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'});
 const page=await browser.newPage({viewport:{width:1800,height:1100}});
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto(pathToFileURL(path.join(__dirname,'EquiTransit-Sequence-Diagrams.html')).href);
 const reports=[];
 for(let i=0;i<5;i++){
  await page.selectOption('#page',String(i));
  reports.push(await page.evaluate(()=>{const s=document.querySelector('#canvas svg');const v=s.viewBox.baseVal;return {title:s.querySelectorAll('text')[1].textContent,participants:s.querySelectorAll('text').length,overflow:[...s.querySelectorAll('text')].filter(t=>{const b=t.getBBox();return b.x<0||b.y<0||b.x+b.width>v.width||b.y+b.height>v.height}).map(t=>t.textContent)}}));
 }
 await page.selectOption('#page','2');await page.click('#fit');
 await page.screenshot({path:path.join(__dirname,'viewer-preview.png')});
 await page.click('#plus');await page.click('#minus');
 const result={pages:reports,errors,passed:errors.length===0&&reports.every(r=>r.overflow.length===0)};
 fs.writeFileSync(path.join(__dirname,'qa.json'),JSON.stringify(result,null,2));
 await browser.close();console.log(JSON.stringify(result));
 if(!result.passed)process.exitCode=1;
})();
