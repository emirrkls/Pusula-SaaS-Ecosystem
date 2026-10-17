// Mechanical route metadata migration; no user data, network or build trigger.
const fs = require('node:fs');
const path = require('node:path');
const {execFileSync} = require('node:child_process');
const root=path.resolve(__dirname,'..');
const routes={
  'quick-start-admin':['home:overview','customers:customer-search','team:settings-sections','ticketDetail:ticket-actions','collection:collection-fees','profile:profile-support'],
  'quick-start-technician':['tickets:ticket-filters','ticketDetail:ticket-customer','photos:photo-add','collection:collection-payment','diagnostics:diagnostics-report'],
  navigation:['home:content','tickets:ticket-filters','profile:profile-support'],
  'customers-manage':['customers:customer-search','customerForm:customer-fields','customers:customer-list'],
  'customer-history':['customers:customer-list','customerDetail:customer-history','customerDetail:customer-history'],
  'customer-contact':['customerContact:customer-contact','customerContact:customer-contact'],
  organizations:['organizations:content','collection:collection-allocation','accounts:content'],
  'tickets-create':['ticketCreate:create-customer','ticketCreate:create-description','ticketCreate:create-save'],
  'tickets-assign':['tickets:ticket-list','ticketDetail:ticket-status','ticketDetail:ticket-history'],
  'tickets-status':['tickets:ticket-filters','ticketDetail:ticket-status','ticketDetail:ticket-history'],
  'tickets-reschedule':['ticketDetail:ticket-schedule','reschedule:content'],
  'tickets-cancel':['ticketDetail:ticket-status','ticketDetail:ticket-actions','tickets:ticket-filters'],
  'tickets-follow-up':['ticketDetail:ticket-status','ticketDetail:ticket-actions'],
  'tickets-notes':['ticketDetail:ticket-notes','ticketDetail:ticket-notes','ticketDetail:ticket-history'],
  'service-photos':['ticketDetail:ticket-customer','photos:photo-add','photos:photo-list'],
  'service-photo-review':['photosArchive:content','photosArchive:content'],
  'service-signature':['ticketDetail:ticket-customer','signature:content'],
  'service-pdf':['ticketDetail:ticket-actions','ticketDetail:ticket-actions'],
  warranty:['collection:collection-warranty','collection:collection-allocation','collection:collection-summary'],
  'quotes-manage':['proposals:content','proposals:content','proposals:content'],
  'quotes-convert':['proposals:content','proposals:content','tickets:ticket-list'],
  'quotes-pdf':['proposals:content','proposals:content'],
  inventory:['catalog:content','catalog:content','catalog:content'],
  barcode:['parts:content','parts:content'],
  'used-parts':['parts:content','parts:content','ticketDetail:ticket-parts'],
  pricing:['collection:collection-fees','collection:collection-summary','collection:collection-payment'],
  discount:['collection:collection-discount','collection:collection-summary','ticketDetail:ticket-actions'],
  'institution-allocation':['collection:collection-allocation','collection:collection-allocation','collection:collection-summary'],
  'payment-methods':['collection:collection-payment','collection:collection-payment','collection:collection-summary'],
  'safe-retry':['collection:collection-payment','collection:collection-payment','tickets:ticket-list'],
  'daily-finance':['finance:content','finance:content'],
  expenses:['expenses:content','expenses:content','finance:content'],
  'current-accounts':['accounts:content','accounts:content','accounts:content'],
  'company-debts':['debts:content','debts:content','debts:content'],
  'fixed-expenses':['fixedExpenses:content','fixedExpenses:content','fixedExpenses:content'],
  'business-assets':['assets:content','assets:content'],
  'finance-reports':['reports:content','reports:content','reports:content'],
  'profit-analysis':['profit:content','profit:content'],
  'team-users':['team:settings-sections','team:content','team:content'],
  vehicles:['vehicles:settings-sections','vehicles:content'],
  'company-settings':['company:settings-sections','company:content'],
  'field-radar':['radar:content','radar:content'],
  'service-network':['network:content','network:content','network:content'],
  notifications:['notifications:content','tickets:ticket-list','notifications:content'],
  'account-login':['login:content','register:content','profile:content'],
  'google-login':['login:content','profile:content'],
  'apple-login':['login:content','login:content'],
  'desktop-password':['profile:content','desktopPassword:content'],
  plans:['plans:content','plans:content','profile:profile-support'],
  subscriptions:['plans:content','plans:content','profile:content'],
  'appearance-keyboard':['profile:content','customerForm:customer-fields','profile:profile-support'],
  'support-request':['supportForm:support-form','supportForm:support-attachments','supportForm:support-submit'],
  'support-replies':['support:support-requests','support:support-requests','support:support-requests'],
  'support-attachments':['supportForm:support-attachments','supportForm:support-attachments','supportForm:support-attachments'],
  'connection-report':['diagnostics:diagnostics-report','diagnostics:diagnostics-report','diagnostics:diagnostics-report'],
  'account-delete':['profile:content','plans:content','profile:profile-delete']
};
const screens=new Set(['home','customers','customerForm','customerDetail','customerContact','tickets','ticketCreate','ticketDetail','reschedule','photos','photosArchive','signature','parts','collection','catalog','proposals','finance','expenses','accounts','organizations','debts','fixedExpenses','assets','reports','profit','team','vehicles','company','radar','network','notifications','profile','plans','login','register','desktopPassword','support','supportForm','diagnostics']);
const file=path.join(root,'guides/mobile-guide.json');
const catalog=JSON.parse(fs.readFileSync(file,'utf8'));
if(catalog.topics.length!==Object.keys(routes).length)throw Error('Tour topic set mismatch');
for(const topic of catalog.topics){
  const values=routes[topic.id];
  if(!values||values.length!==topic.steps.length)throw Error('Missing step route: '+topic.id);
  topic.revision=Math.max(2,topic.revision);
  topic.steps.forEach((step,i)=>{const [screen,target]=values[i].split(':');if(!screens.has(screen)||!target)throw Error('Invalid tour route');step.tour={screen,target};});
  // These native destinations exist only on iOS today. Do not point Android
  // users at an unrelated finance tab or pretend a reschedule form exists.
  if(['tickets-reschedule','company-debts','business-assets'].includes(topic.id)) {
    topic.platforms=topic.platforms.filter(p=>p!=='android');
    delete topic.locations.android;delete topic.sources.android;
  }
}
catalog.revision=Math.max(2,catalog.revision);
if(process.argv.includes('--write')){
  fs.writeFileSync(file,JSON.stringify(catalog,null,2)+'\n');
  execFileSync(process.execPath,['scripts/sync-mobile-guide.cjs','--write'],{cwd:root,stdio:'inherit'});
}else{
  const current=JSON.parse(fs.readFileSync(file,'utf8'));
  if(JSON.stringify(current)!==JSON.stringify(catalog))throw Error('Tour metadata needs --write');
  console.log('All 156 tour steps have reviewed routes.');
}
