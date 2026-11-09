import { createFileRoute, Link } from "@tanstack/react-router";
import { SiteFrame } from "@/components/site-frame";

export const Route = createFileRoute("/guides")({ component: Guides });

const SECTIONS = [
  {
    id: "cable",
    title: "الكابل والمنفذ",
    body: [
      "تحتاج كابل USB-A ذكر إلى USB-A ذكر، ومن النوع اللي ينقل بيانات. كثير كوابل «شحن سريع» فيها سلكين بس، وChrome ما راح يشوف الشاشة.",
      "على جيتور T1 المنفذ العلوي تحت القير من جهة السائق. على T2 لازم تحوّل USB1 إلى Device من القائمة الهندسية. على داشينق غالباً منفذ المسند.",
      "من الجوال: أندرويد مع محول OTG وكابل بيانات، والمتصفح Chrome. الآيفون ما يدعم WebUSB.",
    ],
  },
  {
    id: "adb",
    title: "تفعيل ADB بدون ما تخرب القائمة",
    body: [
      "الكود يُطلب من تطبيق الهاتف المدمج في الشاشة، مو من جوالك. اكتب الأرقام كما هي بما فيها النجمات.",
      "على T1 بعد الكود *#20241130# تدخل إعداد التشفير وكلمة المرور تاريخ السيارة: 20261108 يعني 8 نوفمبر 2026. الزر الأيسر للتأكيد في أغلب النسخ.",
      "فعّل مفتاح ADB ثم اطلع. لا تغيّر خيارات ثانية في القائمة الهندسية إذا ما تعرف وظيفتها.",
    ],
  },
  {
    id: "allow",
    title: "رسالة السماح على الشاشة",
    body: [
      "أول اتصال يولّد مفتاح داخل المتصفح ويرسله للشاشة. الشاشة تسأل إذا تثق في هذا الكمبيوتر. اضغط سماح، ويفضل «دائماً» عشان المرة الجاية ما توقف.",
      "إذا الصفحة وقفت على «بانتظار الموافقة» شوف الشاشة مو اللابتوب. أحياناً الرسالة تختفي خلف قائمة أخرى.",
    ],
  },
  {
    id: "apk",
    title: "أي ملف ينفع",
    body: [
      "ارفع APK واحد. صيغ XAPK و APKS حزم مقسومة وما تنثبّت من الأداة هذي.",
      "الموقع ما يستضيف يوتيوب ولا نتفلكس ولا أي تطبيق. تجيب الملف من مصدر رسمي أو متجر تثق فيه، والتثبيت يصير محلياً بين متصفحك والشاشة.",
      "بعد التثبيت التطبيق قد ما يطلع في شبكة الوكالة. من صفحة التثبيت، وبعد الاتصال، تقدر تشغّله بالاسم. أو تثبّت لانشر ثم تفتحه منه.",
    ],
  },
  {
    id: "net",
    title: "الإنترنت والذاكرة",
    body: [
      "يوتيوب ونتفلكس يحتاجون نت على الشاشة نفسها. وزّع هوتسبوت من الجوال واربط الشاشة عليه من إعدادات الواي فاي.",
      "لا تعبّي التخزين الداخلي. إذا امتلأ، بعض الشاشات تسوي رجوع إعدادات. احذف اللي ما تحتاجه، أو انقل الخرائط لذاكرة خارجية إذا النظام يسمح.",
    ],
  },
  {
    id: "after",
    title: "بعد ما تخلّص",
    body: [
      "إذا وقفت الفلاشة أو أندرويد أوتو السلكي، أرجع منفذ USB من Device إلى Host من نفس القائمة، ثم أعد تشغيل الشاشة.",
      "تقدر تحذف التطبيقات من صفحة التثبيت. هذا يرجّع الشاشة أقرب لوضع الوكالة، بدون فورمات.",
      "الأداة ما تلمس كمبيوتر المحرك. مع ذلك بعض الوكلاء يرفضون دعم الشاشة إذا شافوا تطبيقات خارجية. احذفها قبل ما تراجع الصيانة إذا يهمك هذا.",
    ],
  },
];

function Guides() {
  return (
    <SiteFrame>
      <div className="mx-auto grid max-w-6xl gap-8 px-4 py-10 md:grid-cols-12 md:px-6">
        <aside className="md:col-span-4">
          <p className="font-mono text-xs tracking-widest text-copper" dir="ltr">
            FIELD NOTES
          </p>
          <h1 className="mt-2 text-3xl font-semibold tracking-tight">الدليل</h1>
          <p className="mt-3 text-sm text-muted">
            نفس مسار أصحاب الشاشات الصينية: قائمة هندسية، كابل، ثم تثبيت. مكتوب
            كخطوات شغل مو كإعلان.
          </p>
          <ul className="mt-5 space-y-1">
            {SECTIONS.map((section, index) => (
              <li key={section.id}>
                <a
                  href={`#${section.id}`}
                  className="flex min-h-11 items-center gap-3 rounded-md px-2 text-sm text-muted hover:bg-surface hover:text-fg"
                >
                  <span className="font-mono text-xs text-copper" dir="ltr">
                    {String(index + 1).padStart(2, "0")}
                  </span>
                  {section.title}
                </a>
              </li>
            ))}
          </ul>
        </aside>
        <div className="space-y-10 md:col-span-8">
          {SECTIONS.map((section) => (
            <section key={section.id} id={section.id} className="scroll-mt-20">
              <h2 className="text-xl font-semibold">{section.title}</h2>
              <div className="mt-3 space-y-3 text-sm leading-relaxed text-muted">
                {section.body.map((paragraph) => (
                  <p key={paragraph}>{paragraph}</p>
                ))}
              </div>
            </section>
          ))}
          <p className="text-sm">
            <Link to="/install" search={{ car: "jetour-t1" }} className="text-copper">
              انتقل لطاولة التثبيت
            </Link>
          </p>
        </div>
      </div>
    </SiteFrame>
  );
}
