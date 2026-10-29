WallSync 2.0  |  برمجة وتطوير: محمد عبدالله العمري  -  730215825

العربية
1) ثبّت التطبيق على الهاتفين.
2) في الهاتف الأول اضغط "توليد رمز عشوائي" ثم "مشاركة"، وفي الهاتف الثاني اكتب الرمز واضغط "ربط".
3) عند الربط يختفي زر التوليد ويظهر "تغيير الهاتف المرتبط".
4) عند تغيير الخلفية يصلك إشعار: "تم تغيير الخلفية بالهاتف الآخر بنجاح".
5) قفل التطبيق بالبصمة/الوجه/رمز الهاتف يعمل تلقائياً ويمكن إيقافه من بطاقة "الأمان".

English
Install on both phones. Generate a code on phone A, share it, enter it on phone B.
After linking, the generate button is replaced by "Change linked phone".
You get a notification when the wallpaper was changed on the other phone.
App lock (fingerprint / face / device PIN) is on by default (Security card).
Languages: English, Arabic, French, Spanish, Turkish (follows the phone language).

Firebase Realtime Database rules (recommended - the room list can no longer be listed):
{
  "rules": {
    "wp": {
      "$code": { ".read": true, ".write": true }
    }
  }
}
