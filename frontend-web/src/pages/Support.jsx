import React from 'react';
import { Mail, Phone, Clock } from 'lucide-react';

const Support = () => {
  return (
    <section className="pt-36 pb-20 bg-gray-50 min-h-screen">
      <div className="container mx-auto px-4 md:px-8 max-w-4xl">
        <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-6 md:p-10">
          <h1 className="text-3xl md:text-4xl font-bold text-brand-dark mb-4">Destek</h1>
          <p className="text-gray-600 mb-8">
            Pusula Service urunu ile ilgili teknik sorunlar, hesap islemleri ve genel destek
            talepleriniz icin bize ulasabilirsiniz.
          </p>

          <div className="grid gap-6 md:grid-cols-2">
            <div className="rounded-xl border border-gray-200 p-5">
              <div className="flex items-center gap-3 mb-2">
                <Mail className="w-5 h-5 text-brand-cyan" />
                <h2 className="text-lg font-semibold text-brand-dark">E-posta</h2>
              </div>
              <a className="text-brand-cyan hover:underline break-all" href="mailto:pusulaiklimlendirme.didim@gmail.com">
                pusulaiklimlendirme.didim@gmail.com
              </a>
            </div>

            <div className="rounded-xl border border-gray-200 p-5">
              <div className="flex items-center gap-3 mb-2">
                <Phone className="w-5 h-5 text-brand-cyan" />
                <h2 className="text-lg font-semibold text-brand-dark">Telefon</h2>
              </div>
              <a className="text-brand-cyan hover:underline" href="tel:+905400250925">
                +90 540 025 09 25
              </a>
            </div>
          </div>

          <div className="rounded-xl border border-gray-200 p-5 mt-6">
            <div className="flex items-center gap-3 mb-2">
              <Clock className="w-5 h-5 text-brand-cyan" />
              <h2 className="text-lg font-semibold text-brand-dark">Calisma Saatleri</h2>
            </div>
            <p className="text-gray-700">Pazartesi - Cumartesi: 09:00 - 18:00</p>
            <p className="text-gray-700">Pazar: Acil kayitlar icin e-posta uzerinden</p>
          </div>
        </div>
      </div>
    </section>
  );
};

export default Support;
