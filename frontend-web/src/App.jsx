import React, { useEffect } from 'react';
import { BrowserRouter as Router, Routes, Route, useLocation } from 'react-router-dom';
import Navbar from './components/Navbar';
import Footer from './components/Footer';
import FloatingContactButtons from './components/FloatingContactButtons';
import Home from './pages/Home';
import Services from './pages/Services';
import About from './pages/About';
import Contact from './pages/Contact';
import Support from './pages/Support';
import PrivacyPolicy from './pages/PrivacyPolicy';
import TermsOfUse from './pages/TermsOfUse';
import ServiceLandingPage from './pages/landings/ServiceLandingPage';

const ScrollToTop = () => {
  const { pathname } = useLocation();
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [pathname]);
  return null;
};

function App() {
  return (
    <>
      <ScrollToTop />
      <div className="flex flex-col min-h-screen font-sans text-gray-800 antialiased selection:bg-brand-cyan selection:text-white">
        <Navbar />
        <main className="flex-grow">
          <Routes>
            <Route path="/" element={<Home />} />
            <Route path="/hizmetler" element={<Services />} />
            <Route path="/didim-klima-tamiri" element={<ServiceLandingPage pageKey="tamiri" />} />
            <Route path="/didim-klima-bakimi" element={<ServiceLandingPage pageKey="bakimi" />} />
            <Route path="/didim-klima-montaji" element={<ServiceLandingPage pageKey="montaji" />} />
            <Route path="/altinkum-klima-servisi" element={<ServiceLandingPage pageKey="altinkum" />} />
            <Route path="/akbuk-klima-servisi" element={<ServiceLandingPage pageKey="akbuk" />} />
            <Route path="/bozbuk-klima-servisi" element={<ServiceLandingPage pageKey="bozbuk" />} />
            <Route path="/hakkimizda" element={<About />} />
            <Route path="/iletisim" element={<Contact />} />
            <Route path="/destek" element={<Support />} />
            <Route path="/privacy" element={<PrivacyPolicy />} />
            <Route path="/terms" element={<TermsOfUse />} />
          </Routes>
        </main>
        <Footer />
        {/* Sticky Contact Buttons - Always visible */}
        <FloatingContactButtons />
      </div>
    </>
  );
}

export default App;

